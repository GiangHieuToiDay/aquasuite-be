package com.aquasuite.ims.module.inventory.internal.service.impl;

import com.aquasuite.ims.module.inventory.api.dto.StockDispatchCommand;
import com.aquasuite.ims.module.inventory.api.dto.StockDispatchResult;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.Reservation;
import com.aquasuite.ims.module.inventory.api.dto.MovementType;
import com.aquasuite.ims.module.inventory.api.event.LowStockAlertEvent;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.error.StockDispatchErrorCode;
import com.aquasuite.ims.module.inventory.internal.repository.*;
import com.aquasuite.ims.share.exception.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class InventoryDispatchService {
    private final InventoryRepository inventories;
    private final StockMovementRepository movements;
    private final StockReservationStore reservations;
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final CatalogApi catalog;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    public StockDispatchResult issue(StockDispatchCommand c) {
        if (c == null || c.operationId() == null || c.deliveryId() == null || c.orderId() == null
                || c.orderItemId() == null || c.variantId() == null || c.actorId() == null
                || !"WH_MAIN".equals(c.warehouseCode()) || c.quantity() == null || c.quantity().signum() <= 0
                || c.quantity().compareTo(new BigDecimal("9999999999.99")) > 0
                || c.quantity().stripTrailingZeros().scale() > 2 || c.orderedQty() == null
                || c.orderedQty().compareTo(c.quantity()) < 0
                || c.orderedQty().compareTo(new BigDecimal("9999999999.99")) > 0
                || c.orderedQty().stripTrailingZeros().scale() > 2) throw invalid();
        var normalized = new StockDispatchCommand(c.operationId(), c.deliveryId(), c.orderId(), c.orderItemId(), c.variantId(),
                c.warehouseCode(), c.quantity().stripTrailingZeros(), c.orderedQty().stripTrailingZeros(), c.actorId());
        String payload = encode(normalized);
        reservations.lock("stock-reservation:request:" + c.operationId());
        var prior = movements.findById(c.operationId());
        if (prior.isPresent()) {
            var p = prior.get();
            if (!payload.equals(p.getNote()) || p.getMovementType() != MovementType.STOCK_DISPATCH) throw conflict();
            var op = reservations.operation(c.operationId());
            BigDecimal consumed = BigDecimal.ZERO;
            if (!op.isEmpty()) {
                try { consumed = json.readValue(op.get(1), StockDispatchResult.class).consumedQty(); }
                catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
            }
            return new StockDispatchResult(p.getInventoryId(), c.quantity(), consumed);
        }
        if (!reservations.operation(c.operationId()).isEmpty()) throw conflict();
        reservations.lock("stock-reservation:line:" + c.orderId() + ":" + c.orderItemId());
        // Preserve unowned legacy holds; consume only the source line's recorded hold.
        reservations.lockInventories(c.variantId());
        if (!reservations.coversRecordedHolds(c.variantId())) {
            throw new AppException(StockDispatchErrorCode.INCONSISTENT_HOLDS);
        }
        var stock = inventories.findByVariantIdAndWarehouseCodeForUpdate(c.variantId(), c.warehouseCode())
                .orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        var sku = catalog.getSkuSummary(c.variantId()).orElseThrow(() -> new AppException(CommonErrorCode.RESOURCE_NOT_FOUND));
        // This schema has no authoritative serial inventory. Do not pretend free-text serials were verified.
        if (Boolean.TRUE.equals(sku.serialTracking())) throw new AppException(StockDispatchErrorCode.SERIAL_NOT_SUPPORTED);
        List<Reservation> source = reservations.line(c.orderId(), c.orderItemId());
        if (source.stream().anyMatch(r -> !r.variantId().equals(c.variantId()))) throw conflict();
        var own = source.stream().filter(r -> r.warehouseCode().equals(c.warehouseCode())).findFirst().orElse(null);
        BigDecimal consumed = own == null ? BigDecimal.ZERO : own.heldQty().min(c.quantity());
        if (own != null && (source.size()!=1 || own.heldQty().subtract(consumed).add(own.consumedQty())
                .add(c.quantity()).add(own.releasedQty()).compareTo(own.requestedQty())>0)) throw conflict();
        if (stock.getOnHandQty().compareTo(c.quantity()) < 0
                || stock.getAvailableQty().compareTo(c.quantity().subtract(consumed)) < 0) {
            throw new AppException(StockDispatchErrorCode.INSUFFICIENT_STOCK);
        }
        var result = new StockDispatchResult(stock.getInventoryId(), c.quantity(), consumed);
        if (own == null) {
            if (!source.isEmpty()) throw conflict();
            // Account for unreserved shipments too, so later UC29 cannot allocate already-issued units.
            var shipped = new Reservation(UUID.randomUUID(),c.orderId(),c.orderItemId(),c.variantId(),c.warehouseCode(),
                    c.orderedQty(),BigDecimal.ZERO,c.quantity(),BigDecimal.ZERO,c.orderedQty().subtract(c.quantity()));
            reservations.insert(shipped,stock.getInventoryId());
            reservations.record(c.operationId(),shipped,"CONSUME",payload,encode(result),BigDecimal.ZERO,
                    c.actorId(),"Delivery " + c.deliveryId());
        }
        if (own != null) {
            jdbc.update("update public.stock_reservations set held_qty=held_qty-?, consumed_qty=consumed_qty+?,"
                    + " updated_at=current_timestamp where reservation_id=?", consumed, c.quantity(), own.reservationId());
            var after = new Reservation(own.reservationId(), own.orderId(), own.orderItemId(), own.variantId(),
                    own.warehouseCode(), own.requestedQty(), own.heldQty().subtract(consumed),
                    own.consumedQty().add(c.quantity()), own.releasedQty(), own.waitingQty().subtract(c.quantity().subtract(consumed)).max(BigDecimal.ZERO));
            reservations.record(c.operationId(), after, "CONSUME", payload, encode(result), own.heldQty(),
                    c.actorId(), "Delivery " + c.deliveryId());
        }
        stock.setOnHandQty(stock.getOnHandQty().subtract(c.quantity()));
        stock.setReservedQty(stock.getReservedQty().subtract(consumed));
        inventories.saveAndFlush(stock);
        movements.saveAndFlush(StockMovement.builder().movementId(c.operationId()).inventoryId(stock.getInventoryId())
                .variantId(c.variantId()).movementType(MovementType.STOCK_DISPATCH).quantity(c.quantity().negate())
                .balanceAfter(stock.getOnHandQty()).referenceType("DELIVERY").referenceId(c.deliveryId().toString())
                .createdBy(c.actorId()).note(payload).build());
        if (stock.isLowStock()) events.publishEvent(new LowStockAlertEvent(stock.getVariantId(), stock.getOnHandQty(),
                stock.getAvailableQty(), stock.getMinimumQty()));
        return result;
    }

    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException ex) { throw new IllegalStateException(ex); }
    }
    private AppException invalid() { return new AppException(CommonErrorCode.INVALID_REQUEST_PARAM); }
    private AppException conflict() { return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION); }
}
