package com.aquasuite.ims.module.sales.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Order-owned dispatch facts; lock() joins and requires the caller's transaction. */
public interface OrderDispatchApi {
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
