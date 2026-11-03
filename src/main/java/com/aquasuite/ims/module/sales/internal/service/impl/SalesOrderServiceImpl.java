package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final CrmApi crmApi;
    private final IamApi iamApi;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderCodeGenerator orderCodeGenerator;

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

        // 4. Kiểm tra điều kiện nợ / hạn mức công nợ (Nếu chọn hình thức thanh toán CREDIT)
        if (request.getPaymentMethod() == PaymentMethod.CREDIT) {
            boolean hasOverdueOrders = orderRepository.hasOverdueUnpaidOrders(
                    customer.customerId(),
                    LocalDate.now().minusDays(60)
            );
            if (hasOverdueOrders) {
                log.warn("Customer {} has overdue unpaid balance > 60 days. Blocking credit order.", customer.customerId());
                throw new AppException(SalesErrorCode.CREDIT_LIMIT_EXCEEDED);
            }
        }

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

        // 8. Tính tổng tiền và chiết khấu
        BigDecimal orderDiscount = request.getDiscountAmount() != null ? request.getDiscountAmount() : BigDecimal.ZERO;
        BigDecimal finalTotal = calculatedSubtotal.subtract(orderDiscount);
        if (finalTotal.compareTo(BigDecimal.ZERO) < 0) {
            finalTotal = BigDecimal.ZERO;
        }

        // 9. Khởi tạo trạng thái đơn hàng và trạng thái thanh toán ban đầu
        // Theo chuẩn UC-21 & UC-23: UC-21 chỉ tạo đơn với payment_status = UNPAID và order_status = PROCESSING.
        // Việc thu tiền và xác minh thanh toán do UC-23 xử lý.
        OrderStatus resolvedOrderStatus = OrderStatus.PROCESSING;
        PaymentStatus resolvedPaymentStatus = PaymentStatus.UNPAID;
        LocalDate dueDate = (request.getPaymentMethod() == PaymentMethod.CREDIT)
                ? LocalDate.now().plusDays(30)
                : null;

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
                .orderChannel(request.getOrderChannel() != null ? request.getOrderChannel().name() : OrderChannel.IN_STORE.name())
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

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);

        log.info("Sales Order {} committed successfully (UC-21): totalAmount={}, status={}, paymentStatus={}",
                orderCode, finalTotal, resolvedOrderStatus, resolvedPaymentStatus);

        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(savedItems);
        String staffName = getStaffName(salesStaffId);

        return salesOrderMapper.toDetailResponse(
                savedOrder,
                itemResponses,
                customer.customerName(),
                customer.phone(),
                staffName
        );
    }

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

        return salesOrderMapper.toDetailResponse(
                order,
                itemResponses,
                customerName,
                customerPhone,
                staffName
        );
    }

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

    private UUID resolveCurrentStaffId() {
        try {
            return iamApi.getCurrentUserId();
        } catch (Exception e) {
            log.debug("Current user not authenticated or resolving fallback");
            return null;
        }
    }

    private String getStaffName(UUID staffId) {
        if (staffId == null) return "Nhân viên bán hàng";
        try {
            UserSummaryDto user = iamApi.getUserById(staffId);
            return user != null && user.fullName() != null ? user.fullName() : "Nhân viên bán hàng";
        } catch (Exception e) {
            return "Nhân viên bán hàng";
        }
    }

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
