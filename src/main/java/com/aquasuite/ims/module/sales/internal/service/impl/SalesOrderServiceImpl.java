package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.finance.api.FinanceApi;
import com.aquasuite.ims.module.finance.api.dto.CreatePaymentTransactionCommand;
import com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.api.dto.StockMovementType;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.CancelOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateComboItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateOrderItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateSalesOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SalesOrderFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdatePreDispatchOrderRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesInvoiceSummaryResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
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
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SalesOrderServiceImpl implements SalesOrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceItemRepository salesInvoiceItemRepository;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final CrmApi crmApi;
    private final IamApi iamApi;
    private final FinanceApi financeApi;
    private final SalesOrderMapper salesOrderMapper;
    private final OrderCodeGenerator orderCodeGenerator;

    @Override
    @Transactional
    public SalesOrderDetailResponse createSalesOrder(CreateSalesOrderRequest request) {
        log.info("Processing POS order creation: channel={}, fulfillment={}, paymentMethod={}",
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
            boolean hasOverdueInvoices = orderRepository.hasOverdueUnpaidOrders(
                    customer.customerId(),
                    LocalDate.now().minusDays(60)
            );
            if (hasOverdueInvoices) {
                log.warn("Customer {} has overdue invoices > 60 days. Blocking credit order.", customer.customerId());
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

                // Kiểm tra tồn kho khả dụng
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

        // 9. Xác định trạng thái đơn hàng và thanh toán
        OrderStatus resolvedOrderStatus;
        PaymentStatus resolvedPaymentStatus;
        LocalDate dueDate = null;

        if (request.getPaymentMethod() == PaymentMethod.CREDIT) {
            resolvedOrderStatus = OrderStatus.PROCESSING;
            resolvedPaymentStatus = PaymentStatus.UNPAID;
            dueDate = LocalDate.now().plusDays(30);
        } else {
            BigDecimal collected = request.getCollectedAmount() != null ? request.getCollectedAmount() : BigDecimal.ZERO;
            if (collected.compareTo(finalTotal) >= 0 && finalTotal.compareTo(BigDecimal.ZERO) > 0) {
                resolvedPaymentStatus = PaymentStatus.PAID;
                resolvedOrderStatus = (request.getFulfillmentMethod() == FulfillmentMethod.IN_STORE_PICKUP)
                        ? OrderStatus.COMPLETED
                        : OrderStatus.PROCESSING;
            } else if (collected.compareTo(BigDecimal.ZERO) > 0) {
                resolvedPaymentStatus = PaymentStatus.PARTIALLY_PAID;
                resolvedOrderStatus = OrderStatus.PROCESSING;
            } else {
                resolvedPaymentStatus = PaymentStatus.UNPAID;
                resolvedOrderStatus = OrderStatus.PROCESSING;
            }
        }

        // 10. Cập nhật tồn kho dựa trên Phương thức nhận hàng (Fulfillment Method - Step 12)
        // - Nếu IN_STORE_PICKUP và COMPLETED (nhận tại quầy): Trừ trực tiếp tồn kho thực tế on_hand_qty
        // - Nếu SCHEDULED_DELIVERY hoặc PROCESSING (chờ giao hàng): Chỉ tăng số lượng đặt giữ reserved_qty
        for (OrderItem item : orderItemsToSave) {
            if (resolvedOrderStatus == OrderStatus.COMPLETED) {
                inventoryApi.adjustPhysicalStock(StockAdjustCommand.builder()
                        .variantId(item.getVariantId())
                        .deltaQty(item.getQuantity().negate()) // Số âm để trừ kho vật lý
                        .movementType(StockMovementType.STOCK_DISPATCH)
                        .releaseFromReserved(false)
                        .referenceType("RETAIL_POS")
                        .referenceId(orderCode)
                        .note("Xuất bán lẻ tại quầy POS theo đơn " + orderCode)
                        .actorUserId(salesStaffId)
                        .build());
            } else {
                inventoryApi.reserveStock(item.getVariantId(), item.getQuantity());
            }
        }

        BigDecimal actualPaidAmount = (request.getCollectedAmount() != null && request.getCollectedAmount().compareTo(BigDecimal.ZERO) > 0)
                ? request.getCollectedAmount()
                : BigDecimal.ZERO;

        // 11. Lưu Order Entity & Order Items
        Order order = Order.builder()
                .orderId(orderId)
                .orderCode(orderCode)
                .customerId(customer.customerId())
                .salesStaffId(salesStaffId)
                .orderType(request.getOrderType() != null ? request.getOrderType().name() : OrderType.RETAIL.name())
                .orderChannel(request.getOrderChannel() != null ? request.getOrderChannel().name() : OrderChannel.IN_STORE.name())
                .orderStatus(resolvedOrderStatus.name())
                .paymentStatus(resolvedPaymentStatus.name())
                .orderDate(OffsetDateTime.now())
                .dueDate(dueDate)
                .subtotal(calculatedSubtotal)
                .discountAmount(orderDiscount)
                .totalAmount(finalTotal)
                .paidAmount(actualPaidAmount)
                .deliveryAddress(request.getDeliveryAddress())
                .note(request.getNote())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        Order savedOrder = orderRepository.save(order);
        List<OrderItem> savedItems = orderItemRepository.saveAll(orderItemsToSave);

        // 12. Phát hành Hóa đơn bán lẻ (Sales Invoice & Sales Invoice Items)
        String invoiceCode = generateInvoiceCode();
        OffsetDateTime now = OffsetDateTime.now();
        SalesInvoice invoice = SalesInvoice.builder()
                .invoiceId(UUID.randomUUID())
                .invoiceCode(invoiceCode)
                .orderId(savedOrder.getOrderId())
                .customerId(savedOrder.getCustomerId())
                .invoiceType("RETAIL")
                .issueDate(now)
                .subtotal(savedOrder.getSubtotal())
                .discountAmount(savedOrder.getDiscountAmount())
                .taxAmount(BigDecimal.ZERO)
                .totalAmount(savedOrder.getTotalAmount())
                .status(savedOrder.getPaymentStatus())
                .createdBy(salesStaffId)
                .createdAt(now)
                .build();

        SalesInvoice savedInvoice = salesInvoiceRepository.save(invoice);

        List<SalesInvoiceItem> invoiceItemsToSave = new ArrayList<>();
        for (OrderItem orderItem : savedItems) {
            String desc = orderItem.getProductName() != null ? orderItem.getProductName() : "";
            if (orderItem.getVariantName() != null && !orderItem.getVariantName().isBlank()) {
                desc = desc.isEmpty() ? orderItem.getVariantName() : desc + " - " + orderItem.getVariantName();
            }

            SalesInvoiceItem invoiceItem = SalesInvoiceItem.builder()
                    .invoiceItemId(UUID.randomUUID())
                    .invoiceId(savedInvoice.getInvoiceId())
                    .orderItemId(orderItem.getOrderItemId())
                    .variantId(orderItem.getVariantId())
                    .description(desc)
                    .quantity(orderItem.getQuantity())
                    .unitPrice(orderItem.getUnitPrice())
                    .discountAmount(orderItem.getDiscountAmount() != null ? orderItem.getDiscountAmount() : BigDecimal.ZERO)
                    .taxRate(BigDecimal.ZERO)
                    .lineTotal(orderItem.getLineTotal())
                    .build();
            invoiceItemsToSave.add(invoiceItem);
        }
        salesInvoiceItemRepository.saveAll(invoiceItemsToSave);

        // 13. Ghi nhận giao dịch thanh toán vào bảng transactions (Module Finance) liên kết với khóa invoice_id
        List<PaymentTransactionResponseDto> paymentTransactions = new ArrayList<>();
        if (actualPaidAmount.compareTo(BigDecimal.ZERO) > 0) {
            var txDto = financeApi.recordPaymentTransaction(CreatePaymentTransactionCommand.builder()
                    .orderId(orderId)
                    .invoiceId(savedInvoice.getInvoiceId())
                    .customerId(customer.customerId())
                    .amount(request.getCollectedAmount())
                    .paymentMethod(request.getPaymentMethod() != null ? request.getPaymentMethod().name() : "CASH")
                    .paymentType(resolvedPaymentStatus == PaymentStatus.PAID ? "FULL_PAYMENT" : "DEPOSIT")
                    .category("SALES_RETAIL")
                    .referenceNo(orderCode)
                    .description("Thu tiền đơn bán lẻ quầy POS mã " + orderCode + " theo hóa đơn " + invoiceCode)
                    .createdBy(salesStaffId)
                    .status("VERIFIED")
                    .build());
            if (txDto != null) {
                paymentTransactions.add(txDto);
            }
        }

        log.info("Sales Order {} (Invoice {}) committed successfully: totalAmount={}, status={}, paymentStatus={}, paidAmount={}",
                orderCode, invoiceCode, finalTotal, resolvedOrderStatus, resolvedPaymentStatus, actualPaidAmount);

        List<SalesOrderItemResponse> itemResponses = salesOrderMapper.toItemResponseList(savedItems);
        String staffName = getStaffName(salesStaffId);
        SalesInvoiceSummaryResponse invoiceSummary = salesOrderMapper.toInvoiceSummary(savedInvoice);

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                savedOrder,
                itemResponses,
                customer.customerName(),
                customer.phone(),
                staffName,
                actualPaidAmount,
                paymentTransactions
        );
        response.setInvoice(invoiceSummary);
        return response;
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

        // Lấy lịch sử giao dịch thanh toán từ module Finance
        List<PaymentTransactionResponseDto> txList = financeApi.getTransactionsByOrderId(orderId);
        BigDecimal paidAmount = order.getPaidAmount();
        if (paidAmount == null || (paidAmount.compareTo(BigDecimal.ZERO) == 0 && !txList.isEmpty())) {
            paidAmount = txList.stream()
                    .map(PaymentTransactionResponseDto::amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }

        SalesInvoice invoice = salesInvoiceRepository.findByOrderId(orderId).orElse(null);
        SalesInvoiceSummaryResponse invoiceSummary = invoice != null ? salesOrderMapper.toInvoiceSummary(invoice) : null;

        SalesOrderDetailResponse response = salesOrderMapper.toDetailResponse(
                order,
                itemResponses,
                customerName,
                customerPhone,
                staffName,
                paidAmount,
                txList
        );
        response.setInvoice(invoiceSummary);
        return response;
    }

    @Override
    @Transactional
    public SalesOrderDetailResponse updatePreDispatch(UUID orderId, UpdatePreDispatchOrderRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot edit order {} in status {}", orderId, order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_CANNOT_BE_EDITED);
        }

        if (request.getDeliveryAddress() != null && !request.getDeliveryAddress().isBlank()) {
            order.setDeliveryAddress(request.getDeliveryAddress().trim());
        }
        if (request.getNote() != null) {
            order.setNote(request.getNote().trim());
        }

        Order updated = orderRepository.save(order);
        return getSalesOrderDetail(updated.getOrderId());
    }

    @Override
    @Transactional
    public void cancelOrder(UUID orderId, CancelOrderRequest request) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        if (!OrderStatus.PROCESSING.name().equalsIgnoreCase(order.getOrderStatus())) {
            log.warn("Cannot cancel order {} with status {}", orderId, order.getOrderStatus());
            throw new AppException(SalesErrorCode.ORDER_CANNOT_BE_CANCELLED);
        }

        // Đơn PROCESSING là đơn đang đặt giữ reserved_qty -> Giải phóng lượng tồn kho đã đặt giữ
        List<OrderItem> items = orderItemRepository.findByOrderId(orderId);
        for (OrderItem item : items) {
            if (item.getVariantId() != null && item.getQuantity() != null) {
                inventoryApi.releaseReservedStock(item.getVariantId(), item.getQuantity());
            }
        }

        // Cập nhật trạng thái và lý do hủy cho Order
        order.setOrderStatus(OrderStatus.CANCELLED.name());
        String reasonNote = "[CANCELLED: " + request.getCancelReason() + "]";
        order.setNote(order.getNote() != null ? order.getNote() + " | " + reasonNote : reasonNote);
        orderRepository.save(order);

        // Đồng thời cập nhật trạng thái Hóa đơn sang CANCELLED
        salesInvoiceRepository.findByOrderId(orderId).ifPresent(inv -> {
            inv.setStatus("CANCELLED");
            salesInvoiceRepository.save(inv);
        });

        log.info("Order {} and invoice successfully cancelled and reserved stock released", order.getOrderCode());
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

    private String generateInvoiceCode() {
        String datePart = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String prefix = "INV-" + datePart + "-";

        long currentCount = salesInvoiceRepository.countByInvoiceCodeStartingWith(prefix);
        long nextSequence = currentCount + 1;

        String code = String.format("%s%04d", prefix, nextSequence);
        while (salesInvoiceRepository.existsByInvoiceCode(code)) {
            nextSequence++;
            code = String.format("%s%04d", prefix, nextSequence);
        }

        return code;
    }
}
