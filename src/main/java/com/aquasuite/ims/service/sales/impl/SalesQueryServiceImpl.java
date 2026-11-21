package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.sales.response.*;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.entity.sales.OrderPaymentMilestone;
import com.aquasuite.ims.entity.sales.SalesInvoice;
import com.aquasuite.ims.enums.sales.OrderModificationRequestStatus;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.enums.sales.PaymentStatus;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.sales.SalesErrorCode;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderModificationRequestRepository;
import com.aquasuite.ims.repository.sales.OrderPaymentMilestoneRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.repository.sales.SalesInvoiceRepository;
import com.aquasuite.ims.service.sales.SalesQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SalesQueryServiceImpl implements SalesQueryService {

    private final OrderRepository orderRepository;
    private final SalesInvoiceRepository salesInvoiceRepository;
    private final OrderPaymentMilestoneRepository orderPaymentMilestoneRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderModificationRequestRepository orderModificationRequestRepository;
    private final JdbcTemplate jdbcTemplate;

    // ── Payment & Debt Queries (Finance) ────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(this::toPaymentInfo);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findForPaymentUpdate(orderId).map(this::toPaymentInfo);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        return orderPaymentMilestoneRepository.findByOrderIdOrderBySortOrderAsc(orderId).stream()
                .sorted(Comparator
                        .comparing((OrderPaymentMilestone m) -> m.getSortOrder() != null ? m.getSortOrder() : Integer.MAX_VALUE)
                        .thenComparing(OrderPaymentMilestone::getMilestoneId))
                .map(m -> SalesOrderMilestoneDto.builder()
                        .milestoneId(m.getMilestoneId())
                        .orderId(m.getOrderId())
                        .stageName(m.getStageName())
                        .percent(m.getPercent())
                        .amount(m.getAmount())
                        .sortOrder(m.getSortOrder())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderItemSummaryDto> getOrderItemSummary(UUID orderItemId) {
        if (orderItemId == null) return Optional.empty();
        return orderItemRepository.findById(orderItemId).flatMap(item -> orderRepository.findById(item.getOrderId())
                .map(order -> SalesOrderItemSummaryDto.builder()
                        .orderItemId(item.getOrderItemId())
                        .orderId(order.getOrderId())
                        .orderCode(order.getOrderCode())
                        .variantId(item.getVariantId())
                        .sku(item.getSku())
                        .productName(item.getProductName())
                        .variantName(item.getVariantName())
                        .build()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderDebtDto> findOrderById(UUID orderId) {
        if (orderId == null) return Optional.empty();
        return orderRepository.findById(orderId).map(this::toOrderDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesOrderDebtDto> findOrdersByCustomerIds(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return orderRepository.findByCustomerIdIn(customerIds).stream().map(this::toOrderDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceDebtDto> findInvoicesByCustomerIds(Collection<UUID> customerIds) {
        if (customerIds == null || customerIds.isEmpty()) {
            return List.of();
        }
        return salesInvoiceRepository.findByCustomerIdIn(customerIds).stream().map(this::toInvoiceDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceDebtDto> findElectronicallyIntegratedInvoicesByOrderId(UUID orderId) {
        if (orderId == null) {
            return List.of();
        }
        return salesInvoiceRepository.findElectronicallyIntegratedInvoicesByOrderId(orderId).stream()
                .map(this::toInvoiceDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceDebtDto> findInvoicesByIds(Collection<UUID> invoiceIds) {
        if (invoiceIds == null || invoiceIds.isEmpty()) {
            return List.of();
        }
        return salesInvoiceRepository.findByInvoiceIdIn(invoiceIds).stream()
                .map(this::toInvoiceDto)
                .toList();
    }

    @Override
    @Transactional
    public void updatePaymentStatus(UUID orderId, String paymentStatus) {
        if (orderId == null) {
            throw new IllegalArgumentException("Order ID must not be null");
        }
        if (paymentStatus == null || paymentStatus.isBlank()) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, String.valueOf(paymentStatus));
        }

        PaymentStatus resolvedStatus;
        try {
            resolvedStatus = PaymentStatus.valueOf(paymentStatus.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new AppException(SalesErrorCode.INVALID_PAYMENT_STATUS, paymentStatus);
        }

        Order order = orderRepository.findForPaymentUpdate(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        order.setPaymentStatus(resolvedStatus.name());
        order.setUpdatedAt(OffsetDateTime.now());
    }

    // ── Customer Trust Query (CRM) ──────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public long countQualifiedOrders(UUID customerId) {
        if (customerId == null) return 0;
        return orderRepository.countByCustomerIdAndOrderStatusAndPaymentStatus(customerId, "COMPLETED", "PAID");
    }

    // ── Warranty Queries (ServiceDesk) ──────────────────────────────────────

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Optional<SalesOrderItemWarrantySnapshotDto> lockOrderItemForWarranty(UUID orderItemId) {
        if (orderItemId == null) {
            return Optional.empty();
        }
        Optional<OrderItem> itemOpt = orderItemRepository.findByIdForUpdate(orderItemId);
        if (itemOpt.isEmpty()) {
            return Optional.empty();
        }
        OrderItem item = itemOpt.get();
        Order order = orderRepository.findById(item.getOrderId()).orElse(null);
        if (order == null) {
            return Optional.empty();
        }
        return Optional.of(SalesOrderItemWarrantySnapshotDto.builder()
                .orderItemId(item.getOrderItemId())
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .orderDate(order.getOrderDate())
                .customerId(order.getCustomerId())
                .deliveryAddress(order.getDeliveryAddress())
                .variantId(item.getVariantId())
                .quantity(item.getQuantity())
                .sku(item.getSku())
                .productName(item.getProductName())
                .variantName(item.getVariantName())
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SalesOrderWarrantySummaryDto> getOrderSummaryWithInvoice(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        return orderRepository.findById(orderId).map(order -> {
            List<SalesInvoice> invoices = salesInvoiceRepository.findByOrderIdOrderByIssueDateDesc(orderId);
            SalesInvoice invoice = invoices.stream()
                    .filter(inv -> "STORE".equalsIgnoreCase(inv.getInvoiceType()))
                    .findFirst()
                    .orElse(invoices.isEmpty() ? null : invoices.get(0));

            return SalesOrderWarrantySummaryDto.builder()
                    .orderId(order.getOrderId())
                    .orderCode(order.getOrderCode())
                    .orderStatus(order.getOrderStatus())
                    .paymentStatus(order.getPaymentStatus())
                    .orderDate(order.getOrderDate())
                    .deliveryAddress(order.getDeliveryAddress())
                    .customerId(order.getCustomerId())
                    .invoiceId(invoice != null ? invoice.getInvoiceId() : null)
                    .invoiceCode(invoice != null ? invoice.getInvoiceCode() : null)
                    .invoiceType(invoice != null ? invoice.getInvoiceType() : null)
                    .invoiceStatus(invoice != null ? invoice.getStatus() : null)
                    .issueDate(invoice != null ? invoice.getIssueDate() : null)
                    .invoiceTotalAmount(invoice != null ? invoice.getTotalAmount() : null)
                    .build();
        });
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> getOrderCodesByIds(Collection<UUID> orderIds) {
        if (orderIds == null || orderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return orderRepository.findAllById(orderIds).stream()
                .collect(Collectors.toMap(Order::getOrderId, Order::getOrderCode, (a, b) -> a));
    }

    // ── Variant Usage Queries (Catalog) ─────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public VariantDocumentUsage getVariantDocumentUsage(UUID variantId) {
        return new VariantDocumentUsage(variantId,
                countVariantTable("goods_receipt_items", variantId),
                countVariantTable("stock_dispatch_items", variantId),
                countVariantTable("quotation_items", variantId),
                countVariantTable("order_items", variantId),
                countVariantTable("sales_invoice_items", variantId),
                countVariantTable("purchase_order_items", variantId),
                countVariantTable("delivery_items", variantId),
                countVariantTable("cart_items", variantId));
    }

    @Override
    @Transactional
    public void removeVariantFromCarts(UUID variantId) {
        jdbcTemplate.update("DELETE FROM public.cart_items WHERE variant_id = ?", variantId);
    }

    private long countVariantTable(String table, UUID variantId) {
        Long result = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM public." + table + " WHERE variant_id = ?", Long.class, variantId);
        return result == null ? 0 : result;
    }

    // ── Dispatch Queries & Records (Fulfillment) ────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public Invoice invoice(UUID id) {
        return invoiceSnapshot(id, false);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InvoiceSummary> issuedInvoices(UUID orderId) {
        return jdbcTemplate.query("select invoice_id,invoice_code,order_id,invoice_type,status from public.sales_invoices"
                        + " where order_id=? and status='ISSUED' order by invoice_code,invoice_id",
                (rs, n) -> new InvoiceSummary(rs.getObject(1, UUID.class), rs.getString(2),
                        rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5)), orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public Invoice invoiceForOrder(UUID orderId) {
        var ids = jdbcTemplate.query("select invoice_id from public.sales_invoices where order_id=? and status='ISSUED'",
                (rs, n) -> rs.getObject(1, UUID.class), orderId);
        if (ids.size() != 1) throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        return invoiceSnapshot(ids.getFirst(), false);
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoice(UUID id) {
        return invoiceSnapshot(id, true);
    }

    private Invoice invoiceSnapshot(UUID id, boolean lock) {
        var headers = jdbcTemplate.query("select invoice_id,invoice_code,order_id,invoice_type,status from public.sales_invoices where invoice_id=?"
                + (lock ? " for update" : ""), (rs, n) -> new Invoice(rs.getObject(1, UUID.class), rs.getString(2),
                rs.getObject(3, UUID.class), rs.getString(4), rs.getString(5), List.of()), id);
        if (headers.isEmpty()) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        var h = headers.getFirst();
        var lines = jdbcTemplate.query("select order_item_id,variant_id,quantity,description from public.sales_invoice_items where invoice_id=? order by invoice_item_id"
                + (lock ? " for update" : ""), (rs, n) -> new Line(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getBigDecimal(3), null, rs.getString(4)), id);
        return new Invoice(h.invoiceId(), h.invoiceCode(), h.orderId(), h.type(), h.status(), lines);
    }

    @Override
    @Transactional(readOnly = true)
    public Snapshot read(UUID id) {
        return snapshot(orderRepository.findById(id).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public Snapshot lock(UUID id) {
        return snapshot(orderRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Snapshot snapshot(Order o) {
        boolean pending = orderModificationRequestRepository.existsByOrderIdAndRequestStatusIn(o.getOrderId(), List.of(
                OrderModificationRequestStatus.PENDING_REVIEW, OrderModificationRequestStatus.PENDING_CANCEL));
        var lines = orderItemRepository.findByOrderId(o.getOrderId()).stream().map(i -> new Line(i.getOrderItemId(),
                i.getVariantId(), i.getQuantity(), i.getSku(), i.getProductName())).toList();
        return new Snapshot(o.getOrderId(), o.getOrderCode(), o.getOrderStatus(), o.getDeliveryAddress(), pending, lines, o.getTotalAmount());
    }

    // ── Helper mappers ──────────────────────────────────────────────────────

    private SalesOrderPaymentInfoDto toPaymentInfo(Order order) {
        boolean isValid = !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus());
        return SalesOrderPaymentInfoDto.builder()
                .orderId(order.getOrderId())
                .orderCode(order.getOrderCode())
                .customerId(order.getCustomerId())
                .storeInvoiceId(salesInvoiceRepository.findByOrderIdAndInvoiceType(order.getOrderId(), "STORE")
                        .map(SalesInvoice::getInvoiceId).orElse(null))
                .orderType(order.getOrderType())
                .orderChannel(order.getOrderChannel())
                .orderStatus(order.getOrderStatus())
                .paymentStatus(order.getPaymentStatus())
                .totalAmount(order.getTotalAmount())
                .depositAmount(order.getDepositAmount())
                .stagedDelivery(order.isStagedDelivery())
                .validForPayment(isValid)
                .build();
    }

    private SalesOrderDebtDto toOrderDto(Order order) {
        return new SalesOrderDebtDto(order.getOrderId(), order.getCustomerId(), order.getOrderCode(),
                order.getOrderChannel(), order.getOrderStatus(), order.getPaymentStatus(),
                order.getSubtotal(), order.getDiscountAmount(), order.getTotalAmount(), order.getDueDate());
    }

    private SalesInvoiceDebtDto toInvoiceDto(SalesInvoice invoice) {
        return new SalesInvoiceDebtDto(
                invoice.getInvoiceId(),
                invoice.getOrderId(),
                invoice.getCustomerId(),
                invoice.getInvoiceCode(),
                invoice.getInvoiceSymbol(),
                invoice.getProviderInvoiceId(),
                invoice.getIssueDate(),
                invoice.getSignedAt(),
                invoice.getCreditTermDays(),
                invoice.getDueDate(),
                invoice.getStatus(),
                invoice.getSubtotal(),
                invoice.getDiscountAmount(),
                invoice.getTaxAmount(),
                invoice.getTotalAmount(),
                invoice.getOriginalInvoiceId(),
                invoice.getInvoiceType()
        );
    }
}
