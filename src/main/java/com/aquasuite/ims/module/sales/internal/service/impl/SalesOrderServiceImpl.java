package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationCommand;
import com.aquasuite.ims.module.finance.api.dto.CreditAuthorizationDecision;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.service.SalesOrderService;
import com.aquasuite.ims.module.sales.internal.service.helper.OrderCodeGenerator;
import com.aquasuite.ims.share.exception.AppException;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Tạo đơn tại quầy và cung cấp danh sách, chi tiết đơn hàng bán. */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceItemRepository salesInvoiceItemRepository;
    private final CreditControlApi creditControlApi;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final CrmApi crmApi;
    private final IamApi iamApi;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderCodeGenerator orderCodeGenerator;

    /**
     * Tạo đơn tại quầy: kiểm tra khách, tính tổng, kiểm tra mua nợ nếu cần,
     * giữ tồn kho và lưu đơn cùng hóa đơn cửa hàng trong một transaction.
     * @param request khách hàng, hàng hóa và thông tin đơn cần tạo
     * @return chi tiết đơn và mã hóa đơn cửa hàng
     * @throws AppException nếu dữ liệu, khách, hàng hóa hoặc điều kiện mua nợ không hợp lệ
     */
    @Override
    @Transactional
    public SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request) {
        log.info("Processing POS order creation (UC-21): channel={}, fulfillment={}, paymentMethod={}",
                request.getOrderChannel(), request.getFulfillmentMethod(), request.getPaymentMethod());

        // 1. Kiểm tra danh sách mặt hàng
        boolean hasItems = request.getItems() != null && !request.getItems().isEmpty();
        boolean hasCombos = request.getCombos() != null && !request.getCombos().isEmpty();
        if (!hasItems && !hasCombos) {
            throw new AppException(SalesErrorCode.ORDER_ITEMS_EMPTY);
        }

        // 2. Định danh khách hàng (Mặc định Walk-in Customer nếu không cung cấp)
        CustomerSummaryDto customer = resolveCustomer(request);

        // 3. Nhân viên bán hàng hiện tại
        UUID salesStaffId = resolveCurrentStaffId();
        if (salesStaffId == null) {
            throw new AppException(SalesErrorCode.ACTOR_REQUIRED);
        }
        if (request.getPaymentMethod() == null || request.getPaymentMethod() == PaymentMethod.CARD) {
            throw new AppException(SalesErrorCode.UNSUPPORTED_PAYMENT_METHOD);
        }

        // Dùng cùng khóa theo khách với thao tác chặn mua hàng và kiểm tra công nợ.
        creditControlApi.requireCustomerOrderAllowed(customer.customerId());

        // 5. Chuẩn bị đơn hàng
        UUID orderId = UUID.randomUUID();
        String orderCode = orderCodeGenerator.generateOrderCode();
        List<OrderItem> orderItemsToSave = new ArrayList<>();
        BigDecimal calculatedSubtotal = BigDecimal.ZERO;

        // 6. Kiểm tra & chuẩn bị các biến thể SKU lẻ
        if (hasItems) {
            for (CreateOrderItemRequest itemReq : request.getItems()) {
                CatalogSkuSummaryDto skuDto = catalogApi.getSkuSummary(itemReq.getVariantId())
                        .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, itemReq.getVariantId()));

                if ("INACTIVE".equalsIgnoreCase(skuDto.status())) {
                    throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, skuDto.sku());
                }

                // Kiểm tra tồn kho khả dụng (available_qty = on_hand_qty - reserved_qty)
                boolean isStockAvailable = inventoryApi.checkAvailability(skuDto.variantId(), itemReq.getQuantity());
                if (!isStockAvailable) {
                    log.warn("Insufficient stock for variant SKU={}, requiredQty={}", skuDto.sku(), itemReq.getQuantity());
                    throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK, skuDto.sku() + " (" + skuDto.variantName() + ")");
                }

                BigDecimal unitPrice = skuDto.sellingPrice() != null ? skuDto.sellingPrice() : BigDecimal.ZERO;
                BigDecimal itemDiscount = itemReq.getDiscountAmount() != null ? itemReq.getDiscountAmount() : BigDecimal.ZERO;
                BigDecimal lineTotal = unitPrice.multiply(itemReq.getQuantity()).subtract(itemDiscount);
                if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
                    lineTotal = BigDecimal.ZERO;
                }

                OrderItem orderItem = OrderItem.builder()
                        .orderItemId(UUID.randomUUID())
                        .orderId(orderId)
                        .variantId(skuDto.variantId())
                        .sku(skuDto.sku())
                        .productName(skuDto.productName() != null ? skuDto.productName() : skuDto.variantName())
                        .variantName(skuDto.variantName())
                        .quantity(itemReq.getQuantity())
                        .unitPrice(unitPrice)
                        .discountAmount(itemDiscount)
                        .lineTotal(lineTotal)
                        .build();

                orderItemsToSave.add(orderItem);
                calculatedSubtotal = calculatedSubtotal.add(lineTotal);
            }
        }

        // 7. Kiểm tra & chuẩn bị các gói Combo
        if (hasCombos) {
            for (CreateComboItemRequest comboReq : request.getCombos()) {
                CatalogBundleSummaryDto bundleDto = catalogApi.getBundleSummary(comboReq.getComboId())
                        .orElseThrow(() -> new AppException(SalesErrorCode.COMBO_NOT_FOUND, comboReq.getComboId()));

                int comboQty = Math.max(comboReq.getQuantity(), 1);

                // Kiểm tra tồn kho khả dụng cho toàn bộ component SKU trong gói combo
                if (bundleDto.items() != null) {
                    for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundleDto.items()) {
                        BigDecimal requiredQty = BigDecimal.valueOf((long) comp.quantity() * comboQty);
                        boolean isCompAvailable = inventoryApi.checkAvailability(comp.variantId(), requiredQty);
                        if (!isCompAvailable) {
                            log.warn("Insufficient stock for combo component SKU={}, combo={}", comp.sku(), bundleDto.bundleName());
                            throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK,
                                    comp.sku() + " (thuộc combo " + bundleDto.bundleName() + ")");
                        }
                    }

                    // Ghi nhận snapshot order items cho từng component
                    for (CatalogBundleSummaryDto.CatalogBundleItemDto comp : bundleDto.items()) {
                        BigDecimal totalCompQty = BigDecimal.valueOf((long) comp.quantity() * comboQty);
                        BigDecimal compUnitPrice = comp.sellingPrice() != null ? comp.sellingPrice() : BigDecimal.ZERO;
                        BigDecimal compLineTotal = compUnitPrice.multiply(totalCompQty);

                        OrderItem comboItem = OrderItem.builder()
                                .orderItemId(UUID.randomUUID())
                                .orderId(orderId)
                                .comboId(bundleDto.bundleId())
                                .variantId(comp.variantId())
                                .sku(comp.sku())
                                .productName(bundleDto.bundleName())
                                .variantName(comp.variantName())
                                .quantity(totalCompQty)
                                .unitPrice(compUnitPrice)
                                .discountAmount(BigDecimal.ZERO)
                                .lineTotal(compLineTotal)
                                .build();

                        orderItemsToSave.add(comboItem);
                        calculatedSubtotal = calculatedSubtotal.add(compLineTotal);
                    }
                }
            }
        }

        // 7. Tính tổng tiền và chiết khấu
        BigDecimal orderDiscount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalTotal = calculatedSubtotal.subtract(orderDiscount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }
        OrderChannel channel = request.getOrderChannel() == null || request.getOrderChannel() == OrderChannel.POS
                ? OrderChannel.COUNTER : request.getOrderChannel();

        // 8. Kiểm tra hạn mức mua nợ (đồng bộ trong cùng transaction tạo đơn)
        if (request.getPaymentMethod() == PaymentMethod.CREDIT) {
            CreditAuthorizationDecision decision = creditControlApi.authorize(new CreditAuthorizationCommand(
                    customer.customerId(), orderId, channel.name(), finalTotal));
            if (decision == null || !decision.approved()) {
                log.warn("Credit order denied for customer {}: reason={}",
                        customer.customerId(), decision != null ? decision.reasonCode() : "NULL");
                throw new AppException(SalesErrorCode.CREDIT_LIMIT_EXCEEDED);
            }
        }

        // 9. Khởi tạo trạng thái đơn hàng và trạng thái thanh toán ban đầu
        // Theo chuẩn UC-21 & UC-23: UC-21 chỉ tạo đơn với payment_status = UNPAID và order_status = PROCESSING.
        // Việc thu tiền và xác minh thanh toán do UC-23 xử lý.
        OrderStatus resolvedOrderStatus = OrderStatus.PROCESSING;
        PaymentStatus resolvedPaymentStatus = PaymentStatus.UNPAID;
        LocalDate dueDate = null; // Hạn nợ theo đợt được tính khi có hóa đơn điện tử tương ứng.

        // 10. Đặt giữ tồn kho (reserved_qty) cho các mặt hàng trong đơn
        for (OrderItem item : orderItemsToSave) {
            inventoryApi.reserveStock(item.getVariantId(), item.getQuantity());
        }

        // 11. Lưu Order Entity & Order Items
        OffsetDateTime now = OffsetDateTime.now();
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .customerId(customer.customerId())
                .salesStaffId(salesStaffId)
                .orderType(request.getOrderType() != null ? request.getOrderType().name() : OrderType.RETAIL.name())
                .orderChannel(channel.name())
                .orderStatus(resolvedOrderStatus.name())
                .paymentStatus(resolvedPaymentStatus.name())
                .orderDate(now)
                .dueDate(dueDate)
                .subtotal(calculatedSubtotal)
                .discountAmount(orderDiscount)
                .totalAmount(finalTotal)
                .deliveryAddress(request.getDeliveryAddress())
                .note(request.getNote())
                .createdAt(now)
                .updatedAt(now)
                .build();

        // Liên kết qua ID nên ghi các bản ghi cha trước khi ghi dòng phụ thuộc.
        Order savedOrder = orderRepository.saveAndFlush(order);
        List<OrderItem> savedItems = orderItemRepository.saveAllAndFlush(orderItemsToSave);
        UUID storeInvoiceId = UUID.randomUUID();
        salesInvoiceRepository.saveAndFlush(SalesInvoice.builder()
                .invoiceId(storeInvoiceId)
                .invoiceCode("STORE-" + orderId)
                .orderId(orderId)
                .customerId(customer.customerId())
                .invoiceType("STORE")
                .issueDate(now)
                .subtotal(calculatedSubtotal)
                .discountAmount(orderDiscount)
                .totalAmount(finalTotal)
                .status("DRAFT")
                .createdBy(salesStaffId)
                .createdAt(now)
                .build());
        salesInvoiceItemRepository.saveAll(savedItems.stream().map(item -> SalesInvoiceItem.builder()
                .invoiceItemId(UUID.randomUUID())
                .invoiceId(storeInvoiceId)
                .orderItemId(item.getOrderItemId())
                .variantId(item.getVariantId())
                .description(item.getProductName() + " - " + item.getVariantName())
                .quantity(item.getQuantity())
                .unitPrice(item.getUnitPrice())
                .discountAmount(item.getDiscountAmount())
                .lineTotal(item.getLineTotal())
                .build()).toList());

        log.info("Sales Order {} committed successfully (UC-21): totalAmount={}, status={}, paymentStatus={}",
                orderCode, finalTotal, resolvedOrderStatus, resolvedPaymentStatus);

        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(savedItems);
        String staffName = getStaffName(salesStaffId);

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                savedOrder,
                itemResponses,
                customer.customerName(),
                customer.phone(),
                staffName
        );
        response.setStoreInvoiceId(storeInvoiceId);
        return response;
    }

    /**
     * Lấy danh sách đơn theo bộ lọc và thông tin phân trang.
     * @param filter điều kiện lọc đơn
     * @param pageable trang và số lượng kết quả
     * @return trang tóm tắt đơn hàng
     */
    @Override
    @Transactional(readOnly = true)
    public Page<SalesOrderSummaryResponse> getSalesOrders(SalesOrderFilterRequest filter, Pageable pageable) {
        Specification<Order> spec = buildFilterSpecification(filter);
        Page<Order> orderPage = orderRepository.findAll(spec, pageable);

        return orderPage.map(order -> {
            int itemCount = orderItemRepository.findByOrderId(order.getOrderId()).size();
            String customerName = "Khách hàng";
            String customerPhone = "";
            try {
                var cusOpt = crmApi.getCustomerById(order.getCustomerId());
                if (cusOpt.isPresent()) {
                    customerName = cusOpt.get().customerName();
                    customerPhone = cusOpt.get().phone();
                }
            } catch (Exception e) {
                log.debug("Customer {} lookup ignored", order.getCustomerId());
            }

            String staffName = getStaffName(order.getSalesStaffId());

            return salesOrderMapper.toSummaryResponse(
                    order,
                    itemCount,
                    customerName,
                    customerPhone,
                    staffName
            );
        });
    }

    /**
     * Lấy thông tin đơn, mặt hàng và hóa đơn cửa hàng nếu có.
     * @param orderId mã đơn cần xem
     * @return chi tiết đơn hàng
     * @throws AppException nếu không tìm thấy đơn
     */
    @Override
    @Transactional(readOnly = true)
    public SalesOrderDetailResponse getSalesOrderDetail(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(items);

        String customerName = "Khách hàng";
        String customerPhone = "";
        try {
            var cusOpt = crmApi.getCustomerById(order.getCustomerId());
            if (cusOpt.isPresent()) {
                customerName = cusOpt.get().customerName();
                customerPhone = cusOpt.get().phone();
            }
        } catch (Exception e) {
            log.debug("Customer {} lookup ignored", order.getCustomerId());
        }

        String staffName = getStaffName(order.getSalesStaffId());

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                order,
                itemResponses,
                customerName,
                customerPhone,
                staffName
        );
        salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, "STORE")
                .ifPresent(invoice -> response.setStoreInvoiceId(invoice.getInvoiceId()));
        return response;
    }

    /** Xác định khách theo mã, số điện thoại hoặc khách vãng lai từ yêu cầu tạo đơn. */
    private CustomerSummaryDto resolveCustomer(CreateSalesOrderRequest request) {
        if (request.getCustomerId() != null) {
            return crmApi.getCustomerById(request.getCustomerId())
                    .orElseThrow(() -> new AppException(SalesErrorCode.CUSTOMER_NOT_FOUND));
        }
        if (request.getCustomerPhone() != null && !request.getCustomerPhone().isBlank()) {
            return crmApi.getCustomerByPhone(request.getCustomerPhone().trim())
                    .orElseGet(crmApi::getOrCreateWalkInCustomer);
        }
        return crmApi.getOrCreateWalkInCustomer();
    }

    /** Lấy mã nhân viên đang đăng nhập; trả null nếu không xác định được. */
    private UUID resolveCurrentStaffId() {
        try {
            return iamApi.getCurrentUserId();
        } catch (Exception e) {
            log.debug("Current user not authenticated or resolving fallback");
            return null;
        }
    }

    /** Lấy tên nhân viên để hiển thị, dùng tên mặc định khi không tra được. */
    private String getStaffName(UUID staffId) {
        if (staffId == null) return "Nhân viên bán hàng";
        try {
            UserSummaryDto user = iamApi.getUserById(staffId);
            return user != null && user.fullName() != null ? user.fullName() : "Nhân viên bán hàng";
        } catch (Exception e) {
            return "Nhân viên bán hàng";
        }
    }

    /** Tạo điều kiện truy vấn đơn hàng từ các trường lọc được cung cấp. */
    private Specification<Order> buildFilterSpecification(SalesOrderFilterRequest filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (filter != null) {
                if (filter.getKeyword() != null && !filter.getKeyword().isBlank()) {
                    String pattern = "%" + filter.getKeyword().trim().toLowerCase() + "%";
                    predicates.add(cb.like(cb.lower(root.get("orderCode")), pattern));
                }
                if (filter.getOrderStatus() != null) {
                    predicates.add(cb.equal(root.get("orderStatus"), filter.getOrderStatus().name()));
                }
                if (filter.getPaymentStatus() != null) {
                    predicates.add(cb.equal(root.get("paymentStatus"), filter.getPaymentStatus().name()));
                }
                if (filter.getOrderType() != null) {
                    predicates.add(cb.equal(root.get("orderType"), filter.getOrderType().name()));
                }
                if (filter.getFromDate() != null) {
                    OffsetDateTime fromDateTime = filter.getFromDate().atStartOfDay().atOffset(ZoneOffset.UTC);
                    predicates.add(cb.greaterThanOrEqualTo(root.get("orderDate"), fromDateTime));
                }
                if (filter.getToDate() != null) {
                    OffsetDateTime toDateTime = filter.getToDate().atTime(LocalTime.MAX).atOffset(ZoneOffset.UTC);
                    predicates.add(cb.lessThanOrEqualTo(root.get("orderDate"), toDateTime));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}
