package com.aquasuite.ims.module.inventory.internal.service.impl;

import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.inventory.api.dto.SkuReservationStock;
import com.aquasuite.ims.module.inventory.api.event.LowStockAlertEvent;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.error.InventoryErrorCode;
import com.aquasuite.ims.module.inventory.internal.error.ReservationErrorCode;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockReservationStore;
import com.aquasuite.ims.share.exception.AppException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class StockReservationService implements StockReservationApi {
    private final InventoryRepository inventories;
    private final StockReservationStore store;
    private final ObjectMapper json;
    private final ApplicationEventPublisher events;
    private static final BigDecimal MAX = new BigDecimal("9999999999.99");

    @Override
    @Transactional
    public Reservation reserve(Reserve c) {
        return allocate(c,false,false);
    }

    @Override
    @Transactional
    public Reservation reserveForDeposit(Reserve c) {
        return allocate(c,true,false);
    }

    @Override
    @Transactional
    public Reservation reserveDeferredDeposit(Reserve c) {
        return allocate(c,true,true);
    }

    private Reservation allocate(Reserve c, boolean checked, boolean preserveLegacy) {
        if (c == null || c.requestId() == null || c.orderId() == null || c.orderItemId() == null
                || c.variantId() == null || c.actorId() == null || c.warehouseCode() == null
                || c.warehouseCode().isBlank() || c.warehouseCode().length() > 50) invalid();
        quantity(c.requestedQty());
        String warehouse = c.warehouseCode().trim();
        Reserve normalized = new Reserve(c.requestId(), c.orderId(), c.orderItemId(), c.variantId(), warehouse,
                c.requestedQty().stripTrailingZeros(), c.actorId());
        String payload = "RESERVE:" + encode(normalized);
        Reservation replay = replay(c.requestId(), payload);
        if (replay != null) return replay;
        sourceLock(c.orderId(), c.orderItemId());
        List<Reservation> line = store.line(c.orderId(), c.orderItemId());
        if (line.stream().anyMatch(r -> !r.variantId().equals(c.variantId())
                || r.requestedQty().compareTo(c.requestedQty()) != 0)) conflict();
        if (checked) {
            store.lockInventories(c.variantId());
            boolean consistent = preserveLegacy ? store.coversRecordedHolds(c.variantId())
                    : store.reconciled(c.variantId());
            if (!consistent) throw new AppException(ReservationErrorCode.INCONSISTENT);
        }
        Inventory inventory = inventory(c.variantId(), warehouse);
        BigDecimal allocated = line.stream().map(r -> r.heldQty().add(r.consumedQty()).add(r.releasedQty()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal increment = c.requestedQty().subtract(allocated).max(BigDecimal.ZERO)
                .min(inventory.getAvailableQty().max(BigDecimal.ZERO));
        Reservation existing = line.stream().filter(r -> r.warehouseCode().equals(warehouse)).findFirst().orElse(null);
        BigDecimal before = existing == null ? BigDecimal.ZERO : existing.heldQty();
        Reservation result = new Reservation(existing == null ? UUID.randomUUID() : existing.reservationId(),
                c.orderId(), c.orderItemId(), c.variantId(), warehouse, c.requestedQty(), before.add(increment),
                existing == null ? BigDecimal.ZERO : existing.consumedQty(),
                existing == null ? BigDecimal.ZERO : existing.releasedQty(),
                c.requestedQty().subtract(allocated).subtract(increment).max(BigDecimal.ZERO));
        if (existing == null) store.insert(result, inventory.getInventoryId());
        else store.quantities(result);
        inventory.setReservedQty(inventory.getReservedQty().add(increment));
        inventories.saveAndFlush(inventory);
        store.record(c.requestId(), result, "RESERVE", payload, encode(result), before, c.actorId(), null);
        if (increment.signum() > 0 && inventory.isLowStock()) {
            events.publishEvent(new LowStockAlertEvent(inventory.getVariantId(), inventory.getOnHandQty(),
                    inventory.getAvailableQty(), inventory.getMinimumQty()));
        }
        return result;
    }

    @Override
    @Transactional
    public Reservation release(Release c) {
        if (c == null || c.requestId() == null || c.reservationId() == null || c.actorId() == null
                || c.reason() == null || c.reason().isBlank() || c.reason().length() > 500) invalid();
        quantity(c.quantity());
        String payload = "RELEASE:" + encode(new Release(c.requestId(), c.reservationId(),
                c.quantity().stripTrailingZeros(), c.reason().trim(), c.actorId()));
        Reservation replay = replay(c.requestId(), payload);
        if (replay != null) return replay;
        Reservation first = required(c.reservationId());
        sourceLock(first.orderId(), first.orderItemId());
        Inventory inventory = inventory(first.variantId(), first.warehouseCode());
        Reservation current = required(c.reservationId());
        if (c.quantity().compareTo(current.heldQty()) > 0) {
            throw new AppException(ReservationErrorCode.EXCESS_RELEASE);
        }
        if (inventory.getReservedQty().compareTo(c.quantity()) < 0) {
            throw new AppException(ReservationErrorCode.INCONSISTENT);
        }
        Reservation result = new Reservation(current.reservationId(), current.orderId(), current.orderItemId(),
                current.variantId(), current.warehouseCode(), current.requestedQty(),
                current.heldQty().subtract(c.quantity()), current.consumedQty(), current.releasedQty().add(c.quantity()),
                current.waitingQty());
        store.quantities(result);
        inventory.setReservedQty(inventory.getReservedQty().subtract(c.quantity()));
        inventories.saveAndFlush(inventory);
        store.record(c.requestId(), result, "RELEASE", payload, encode(result), current.heldQty(), c.actorId(), c.reason().trim());
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<SkuReservationStock> stockByVariant(UUID variantId) {
        if (variantId == null) invalid();
        return store.stockByVariant(variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Reservation> findByOrder(UUID orderId) {
        if (orderId == null) invalid();
        return store.order(orderId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Activity> history(UUID reservationId) {
        if (reservationId == null) invalid();
        required(reservationId);
        return store.history(reservationId);
    }

    private void sourceLock(UUID order, UUID item) { store.lock("stock-reservation:line:" + order + ":" + item); }
    private Inventory inventory(UUID variant, String warehouse) {
        return inventories.findByVariantIdAndWarehouseCodeForUpdate(variant, warehouse)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variant));
    }
    private Reservation required(UUID id) {
        Reservation result = store.find(id);
        if (result == null) throw new AppException(ReservationErrorCode.NOT_FOUND);
        return result;
    }
    private Reservation replay(UUID request, String payload) {
        store.lock("stock-reservation:request:" + request);
        List<String> prior = store.operation(request);
        if (prior.isEmpty()) return null;
        if (!prior.getFirst().equals(payload)) conflict();
        try { return json.readValue(prior.get(1), Reservation.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Invalid reservation ledger result", e); }
    }
    private String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Cannot serialize reservation ledger", e); }
    }
    private void quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0 || value.compareTo(MAX) > 0
                || value.stripTrailingZeros().scale() > 2) invalid();
    }
    private void invalid() { throw new AppException(ReservationErrorCode.INVALID); }
    private void conflict() { throw new AppException(ReservationErrorCode.CONFLICT); }
}
