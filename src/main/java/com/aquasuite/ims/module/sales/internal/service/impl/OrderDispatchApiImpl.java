package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.share.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class OrderDispatchApiImpl implements OrderDispatchApi {
    private final OrderRepository orders;
    private final OrderItemRepository items;
    private final OrderModificationRequestRepository modifications;
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Invoice invoice(UUID id) { return invoiceSnapshot(id,false); }

    @Transactional(readOnly = true)
    public List<InvoiceSummary> issuedInvoices(UUID orderId) {
        return jdbc.query("select invoice_id,invoice_code,order_id,invoice_type,status from public.sales_invoices"
                + " where order_id=? and status='ISSUED' order by invoice_code,invoice_id",
                (rs,n) -> new InvoiceSummary(rs.getObject(1,UUID.class),rs.getString(2),
                        rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5)),orderId);
    }

    @Transactional(readOnly = true)
    public Invoice invoiceForOrder(UUID orderId) {
        var ids=jdbc.query("select invoice_id from public.sales_invoices where order_id=? and status='ISSUED'",
                (rs,n) -> rs.getObject(1,UUID.class),orderId);
        if (ids.size()!=1) throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        return invoiceSnapshot(ids.getFirst(),false);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Invoice lockInvoice(UUID id) { return invoiceSnapshot(id,true); }

    private Invoice invoiceSnapshot(UUID id,boolean lock) {
        var headers=jdbc.query("select invoice_id,invoice_code,order_id,invoice_type,status from public.sales_invoices where invoice_id=?"
                +(lock?" for update":""), (rs,n) -> new Invoice(rs.getObject(1,UUID.class),rs.getString(2),
                rs.getObject(3,UUID.class),rs.getString(4),rs.getString(5),List.of()),id);
        if (headers.isEmpty()) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        var h=headers.getFirst();
        var lines=jdbc.query("select order_item_id,variant_id,quantity,description from public.sales_invoice_items where invoice_id=? order by invoice_item_id"
                +(lock?" for update":""), (rs,n) -> new Line(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getBigDecimal(3),null,rs.getString(4)),id);
        return new Invoice(h.invoiceId(),h.invoiceCode(),h.orderId(),h.type(),h.status(),lines);
    }

    @Transactional(readOnly = true)
    public Snapshot read(UUID id) {
        return snapshot(orders.findById(id).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND)));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Snapshot lock(UUID id) {
        return snapshot(orders.findByIdForUpdate(id)
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND)));
    }

    private Snapshot snapshot(Order o) {
        // Approved modifications are not considered applied until UC26 owns a completion contract.
        boolean pending = modifications.existsByOrderIdAndRequestStatusIn(o.getOrderId(), List.of(
                OrderModificationRequestStatus.PENDING_REVIEW, OrderModificationRequestStatus.PENDING_CANCEL,
                OrderModificationRequestStatus.APPROVED));
        var lines = items.findByOrderId(o.getOrderId()).stream().map(i -> new Line(i.getOrderItemId(),
                i.getVariantId(), i.getQuantity(), i.getSku(), i.getProductName())).toList();
        return new Snapshot(o.getOrderId(), o.getOrderCode(), o.getOrderStatus(), o.getDeliveryAddress(), pending, lines,o.getTotalAmount());
    }
}
