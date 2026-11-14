package com.aquasuite.ims.module.fulfillment.internal.service;

import com.aquasuite.ims.module.fulfillment.internal.entity.*;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryIssueRequest;
import com.aquasuite.ims.module.fulfillment.internal.repository.*;
import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.module.inventory.api.*;
import com.aquasuite.ims.module.finance.api.DeliveryCodCapacityApi;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryIssueService {
    private final OrderDispatchApi sales;
    private final InventoryApi stock;
    private final StockReservationApi reservations;
    private final CatalogApi catalog;
    private final DeliveryCodCapacityApi finance;
    private final IamApi iam;
    private final DeliveryRepository deliveries;
    private final DeliveryItemRepository items;
    private final DeliveryIssueStore store;

    public record Candidate(UUID orderItemId, UUID variantId, String sku, String productName,
                            BigDecimal orderedQty, BigDecimal issuedQty, BigDecimal remainingQty,
                            BigDecimal heldQty, BigDecimal availableQty, boolean serialTracking) {}
    public record Candidates(UUID orderId, String orderCode, String deliveryAddress, boolean eligible,
                             List<Candidate> items, UUID salesInvoiceId, String invoiceCode) {}
    public record Result(UUID deliveryId, String deliveryCode, UUID orderId, Integer batchNo,
                         String deliveryStatus, OffsetDateTime confirmedAt, UUID salesInvoiceId) {}

    @Transactional(readOnly = true)
    public List<OrderDispatchApi.InvoiceSummary> issuedInvoices(UUID orderId) {
        actor();
        sales.read(orderId);
        return sales.issuedInvoices(orderId);
    }

    @Transactional(readOnly = true)
    public Candidates candidates(UUID orderId) {
        actor();
        return invoiceCandidatesView(sales.invoiceForOrder(orderId));
    }

    @Transactional(readOnly = true)
    public Candidates invoiceCandidates(UUID invoiceId) {
        actor();
        return invoiceCandidatesView(sales.invoice(invoiceId));
    }

    private Candidates invoiceCandidatesView(OrderDispatchApi.Invoice invoice) {
        UUID orderId=invoice.orderId();
        var order = sales.read(orderId);
        var invoiceQty=invoiceQuantities(invoice,order);
        var invoiceIssued=store.issuedForInvoice(invoice.invoiceId());
        var issued = store.issued(orderId);
        var held = reservations.findByOrder(orderId);
        var lines = order.lines().stream().filter(l -> invoiceQty.containsKey(l.orderItemId())).map(l -> {
            BigDecimal out = issued.getOrDefault(l.orderItemId(),BigDecimal.ZERO);
            var sku = catalog.getSkuSummary(l.variantId()).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
            var inv = stock.getStockSummary(l.variantId());
            BigDecimal own = held.stream().filter(r -> r.orderItemId().equals(l.orderItemId())
                    && "WH_MAIN".equals(r.warehouseCode())).map(StockReservationApi.Reservation::heldQty)
                    .reduce(BigDecimal.ZERO,BigDecimal::add);
            return new Candidate(l.orderItemId(),l.variantId(),l.sku(),l.productName(),l.quantity(),out,
                    l.quantity().subtract(out).min(invoiceQty.get(l.orderItemId())
                            .subtract(invoiceIssued.getOrDefault(l.orderItemId(),BigDecimal.ZERO))).max(BigDecimal.ZERO),own,
                    inv.filter(i -> "WH_MAIN".equals(i.warehouseCode())).map(i -> i.availableQty()).orElse(BigDecimal.ZERO),
                    Boolean.TRUE.equals(sku.serialTracking()));
        }).toList();
        return new Candidates(orderId,order.orderCode(),order.deliveryAddress(),
                "PROCESSING".equals(order.status()) && !order.modificationPending()
                        && !store.hasAmbiguousLegacy(orderId),lines,invoice.invoiceId(),invoice.invoiceCode());
    }

    @Transactional
    public Result create(UUID orderId, DeliveryIssueRequest request) {
        actor();
        return createForInvoice(sales.invoiceForOrder(orderId).invoiceId(),orderId,request);
    }

    @Transactional
    public Result createFromInvoice(UUID invoiceId, DeliveryIssueRequest request) {
        actor();
        return createForInvoice(invoiceId,sales.invoice(invoiceId).orderId(),request);
    }

    private Result createForInvoice(UUID invoiceId, UUID orderId, DeliveryIssueRequest request) {
        UUID actor = actor();
        store.lockRequest(request.deliveryId());
        var order = sales.lock(orderId);
        var invoice=sales.lockInvoice(invoiceId);
        var invoiceQty=invoiceQuantities(invoice,order);
        var invoiceIssued=store.issuedForInvoice(invoiceId);
        var prior = deliveries.findById(request.deliveryId());
        if (prior.isPresent()) {
            if (!invoiceId.equals(prior.get().getSalesInvoiceId()) || !same(prior.get(),orderId,actor,request)) throw conflict();
            return result(prior.get());
        }
        eligible(order);
        if (request.items().stream().map(DeliveryIssueRequest.Line::orderItemId).distinct().count() != request.items().size()) throw invalid();
        var ordered = order.lines().stream().collect(java.util.stream.Collectors.toMap(OrderDispatchApi.Line::orderItemId,l -> l));
        var out = store.issued(orderId);
        List<DeliveryItem> details = new ArrayList<>();
        for (var l : request.items()) {
            var source = ordered.get(l.orderItemId());
            if (!invoiceQty.containsKey(l.orderItemId()) || l.quantity().compareTo(invoiceQty.get(l.orderItemId())
                    .subtract(invoiceIssued.getOrDefault(l.orderItemId(),BigDecimal.ZERO)))>0) throw conflict();
            if (source == null || l.quantity().compareTo(source.quantity().subtract(out.getOrDefault(l.orderItemId(),BigDecimal.ZERO))) > 0) throw conflict();
            var sku = catalog.getSkuSummary(source.variantId()).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
            if (Boolean.TRUE.equals(sku.serialTracking())) throw new AppException(
                    com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode.SERIAL_DISPATCH_UNSUPPORTED);
            var inv = stock.getStockSummary(source.variantId()).filter(i -> "WH_MAIN".equals(i.warehouseCode()))
                    .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
            details.add(DeliveryItem.builder().deliveryId(request.deliveryId()).orderItemId(l.orderItemId())
                    .variantId(source.variantId()).inventoryId(inv.inventoryId()).quantity(l.quantity()).serialNumbers(List.of()).build());
        }
        var d = Delivery.builder().deliveryId(request.deliveryId()).deliveryCode("DL-"+request.deliveryId())
                .orderId(orderId).salesInvoiceId(invoiceId).warehouseCode(request.warehouseCode()).batchNo(store.nextBatch(orderId))
                .recipientName(request.recipientName()).recipientPhone(request.recipientPhone())
                .deliveryAddress(request.deliveryAddress()).scheduledDate(request.scheduledDate())
                .codAmount(request.codAmount()).codRequired(request.codAmount().signum()>0)
                .note(request.note()).createdBy(actor).build();
        deliveries.saveAndFlush(d);
        items.saveAllAndFlush(details);
        return result(d);
    }

    @Transactional
    public Result confirm(UUID id) {
        UUID actor = actor();
        UUID orderId = requiredOrder(id);
        var order = sales.lock(orderId);
        var d = locked(id);
        if (d.getConfirmedAt() != null) {
            if (!actor.equals(d.getConfirmedBy())) throw conflict();
            return result(d);
        }
        if (!"DRAFT".equals(d.getDeliveryStatus()) || !"WH_MAIN".equals(d.getWarehouseCode())) throw conflict();
        eligible(order);
        UUID invoiceId=d.getSalesInvoiceId()!=null?d.getSalesInvoiceId():sales.invoiceForOrder(orderId).invoiceId();
        var invoice=sales.lockInvoice(invoiceId);
        var invoiceQty=invoiceQuantities(invoice,order);
        var invoiceIssued=store.issuedForInvoice(invoiceId);
        var source = order.lines().stream().collect(java.util.stream.Collectors.toMap(OrderDispatchApi.Line::orderItemId,l -> l));
        var out = store.issued(orderId);
        var lines = items.findByDeliveryIdOrderByDeliveryItemId(id).stream()
                .sorted(Comparator.comparing(DeliveryItem::getVariantId).thenComparing(DeliveryItem::getOrderItemId)).toList();
        if (lines.isEmpty() || lines.stream().map(DeliveryItem::getOrderItemId).distinct().count()!=lines.size()) throw conflict();
        for (var l : lines) {
            if (!invoiceQty.containsKey(l.getOrderItemId()) || l.getQuantity().compareTo(invoiceQty.get(l.getOrderItemId())
                    .subtract(invoiceIssued.getOrDefault(l.getOrderItemId(),BigDecimal.ZERO)))>0) throw conflict();
            var s = source.get(l.getOrderItemId());
            if (s==null || !s.variantId().equals(l.getVariantId()) || l.getQuantity().signum()<=0
                    || l.getQuantity().compareTo(s.quantity().subtract(out.getOrDefault(l.getOrderItemId(),BigDecimal.ZERO)))>0
                    || (l.getSerialNumbers()!=null && !l.getSerialNumbers().isEmpty())) throw conflict();
        }
        if (d.getCodAmount().signum()<0 || d.getCodAmount().stripTrailingZeros().scale()>0
                || !Objects.equals(d.getCodRequired(),d.getCodAmount().signum()>0)) throw conflict();
        if (d.getCodAmount().compareTo(finance.available(orderId,store.commitments(orderId)))>0) throw conflict();
        for (var l : lines) {
            checkReservationAvailability(d, l);
            var r = stock.issue(new com.aquasuite.ims.module.inventory.api.dto.StockDispatchCommand(l.getDeliveryItemId(),id,orderId,l.getOrderItemId(),
                    l.getVariantId(),d.getWarehouseCode(),l.getQuantity(),source.get(l.getOrderItemId()).quantity(),actor));
            if (!r.inventoryId().equals(l.getInventoryId())) throw conflict();
        }
        var now = OffsetDateTime.now().truncatedTo(ChronoUnit.MICROS);
        d.setSalesInvoiceId(invoiceId);
        d.setConfirmedAt(now); d.setConfirmedBy(actor); d.setDispatchDate(now); d.setDeliveryStatus("DISPATCHED");
        deliveries.saveAndFlush(d);
        return result(d);
    }

    @Transactional
    public Result cancel(UUID id) {
        actor();
        sales.lock(requiredOrder(id));
        var d = locked(id);
        if ("CANCELLED".equals(d.getDeliveryStatus()) && d.getConfirmedAt()==null) return result(d);
        if (!"DRAFT".equals(d.getDeliveryStatus()) || d.getConfirmedAt()!=null) throw conflict();
        d.setDeliveryStatus("CANCELLED"); deliveries.saveAndFlush(d);
        return result(d);
    }

    private UUID actor() {
        var a = iam.getCurrentUser();
        if (a==null || a.id()==null) throw new AppException(CommonErrorCode.UNAUTHORIZED);
        if (a.roles()==null || Collections.disjoint(a.roles(),List.of("ACCOUNTANT","SHOP_OWNER"))) throw new AppException(CommonErrorCode.ACCESS_DENIED);
        return a.id();
    }
    /** Diagnostic only: Inventory still validates and consumes stock under its own locks. */
    private void checkReservationAvailability(Delivery delivery, DeliveryItem line) {
        var inventory = stock.getStockSummary(line.getVariantId())
                .filter(s -> delivery.getWarehouseCode().equals(s.warehouseCode()));
        if (inventory.isEmpty() || inventory.get().reservedQty() == null
                || inventory.get().reservedQty().signum() <= 0 || inventory.get().availableQty() == null) return;
        BigDecimal own = reservations.findByOrder(delivery.getOrderId()).stream()
                .filter(r -> line.getOrderItemId().equals(r.orderItemId())
                        && line.getVariantId().equals(r.variantId())
                        && delivery.getWarehouseCode().equals(r.warehouseCode()))
                .map(StockReservationApi.Reservation::heldQty).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (own.add(inventory.get().availableQty().max(BigDecimal.ZERO)).compareTo(line.getQuantity()) < 0)
            throw new AppException(com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode.RESERVATION_REVIEW_REQUIRED);
    }
    private void eligible(OrderDispatchApi.Snapshot o) {
        if (!"PROCESSING".equals(o.status()) || o.modificationPending() || store.hasAmbiguousLegacy(o.orderId())) throw conflict();
    }
    private UUID requiredOrder(UUID id) {
        UUID result = store.orderId(id);
        if (result==null) throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
        return result;
    }
    private Delivery locked(UUID id) { return deliveries.lockById(id).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND)); }
    private Result result(Delivery d) { return new Result(d.getDeliveryId(),d.getDeliveryCode(),d.getOrderId(),d.getBatchNo(),d.getDeliveryStatus(),d.getConfirmedAt(),d.getSalesInvoiceId()); }
    private Map<UUID,BigDecimal> invoiceQuantities(OrderDispatchApi.Invoice invoice,OrderDispatchApi.Snapshot order) {
        if (!order.orderId().equals(invoice.orderId())
                || !"ISSUED".equals(invoice.status()) || invoice.lines().isEmpty()) throw conflict();
        var source=order.lines().stream().collect(java.util.stream.Collectors.toMap(OrderDispatchApi.Line::orderItemId,l -> l));
        Map<UUID,BigDecimal> result=new HashMap<>();
        for(var l:invoice.lines()) {
            var original=source.get(l.orderItemId());
            if(original==null || !original.variantId().equals(l.variantId()) || l.quantity()==null || l.quantity().signum()<=0) throw conflict();
            result.merge(l.orderItemId(),l.quantity(),BigDecimal::add);
        }
        return result;
    }
    private boolean same(Delivery d, UUID order, UUID actor, DeliveryIssueRequest r) {
        if (!order.equals(d.getOrderId()) || !actor.equals(d.getCreatedBy()) || !r.warehouseCode().equals(d.getWarehouseCode())
                || !r.recipientName().equals(d.getRecipientName()) || !r.recipientPhone().equals(d.getRecipientPhone())
                || !r.deliveryAddress().equals(d.getDeliveryAddress()) || !Objects.equals(r.note(),d.getNote())
                || r.codAmount().compareTo(d.getCodAmount())!=0 || !Objects.equals(
                        r.scheduledDate()==null?null:r.scheduledDate().toInstant().truncatedTo(ChronoUnit.MICROS),
                        d.getScheduledDate()==null?null:d.getScheduledDate().toInstant())) return false;
        var saved = items.findByDeliveryIdOrderByDeliveryItemId(d.getDeliveryId());
        return saved.size()==r.items().size() && r.items().stream().map(DeliveryIssueRequest.Line::orderItemId).distinct().count()==r.items().size()
                && r.items().stream().allMatch(l -> saved.stream().anyMatch(s -> s.getOrderItemId().equals(l.orderItemId()) && s.getQuantity().compareTo(l.quantity())==0));
    }
    private AppException invalid() { return new AppException(CommonErrorCode.INVALID_REQUEST_PARAM); }
    private AppException conflict() { return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION); }
}
