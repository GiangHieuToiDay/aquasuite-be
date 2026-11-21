package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.response.*;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Service tra cứu thuần đọc từ Sales dành cho các domain khác (Fulfillment, Finance, CRM, ServiceDesk, Catalog)
 * và cập nhật trạng thái thanh toán đơn hàng.
 * Circuit-breaker ngăn ngừa vòng phụ thuộc bean Sales &lt;-&gt; Finance.
 */
public interface SalesQueryService {

    // ── Payment & Debt Queries (Finance) ────────────────────────────────────

    Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId);

    Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId);

    List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId);

    Optional<SalesOrderItemSummaryDto> getOrderItemSummary(UUID orderItemId);

    Optional<SalesOrderDebtDto> findOrderById(UUID orderId);

    List<SalesOrderDebtDto> findOrdersByCustomerIds(Collection<UUID> customerIds);

    List<SalesInvoiceDebtDto> findInvoicesByCustomerIds(Collection<UUID> customerIds);

    List<SalesInvoiceDebtDto> findElectronicallyIntegratedInvoicesByOrderId(UUID orderId);

    List<SalesInvoiceDebtDto> findInvoicesByIds(Collection<UUID> invoiceIds);

    void updatePaymentStatus(UUID orderId, String paymentStatus);

    // ── Customer Trust Query (CRM) ──────────────────────────────────────────

    long countQualifiedOrders(UUID customerId);

    // ── Warranty Queries (ServiceDesk) ──────────────────────────────────────

    Optional<SalesOrderItemWarrantySnapshotDto> lockOrderItemForWarranty(UUID orderItemId);

    Optional<SalesOrderWarrantySummaryDto> getOrderSummaryWithInvoice(UUID orderId);

    Map<UUID, String> getOrderCodesByIds(Collection<UUID> orderIds);

    // ── Variant Usage Queries (Catalog) ─────────────────────────────────────

    VariantDocumentUsage getVariantDocumentUsage(UUID variantId);

    void removeVariantFromCarts(UUID variantId);

    // ── Dispatch Queries & Records (Fulfillment) ────────────────────────────

    Snapshot read(UUID orderId);

    Snapshot lock(UUID orderId);

    Invoice invoice(UUID invoiceId);

    Invoice invoiceForOrder(UUID orderId);

    Invoice lockInvoice(UUID invoiceId);

    List<InvoiceSummary> issuedInvoices(UUID orderId);

    record InvoiceSummary(UUID invoiceId, String invoiceCode, UUID orderId, String type, String status) {}

    record Invoice(UUID invoiceId, String invoiceCode, UUID orderId, String type, String status, List<Line> lines) {}

    record Line(UUID orderItemId, UUID variantId, BigDecimal quantity, String sku, String productName) {}

    record Snapshot(UUID orderId, String orderCode, String status, String deliveryAddress,
                    boolean modificationPending, List<Line> lines, BigDecimal totalAmount) {}
}
