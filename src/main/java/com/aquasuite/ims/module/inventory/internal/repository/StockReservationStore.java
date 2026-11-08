package com.aquasuite.ims.module.inventory.internal.repository;

import com.aquasuite.ims.module.inventory.api.StockReservationApi.Reservation;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.Activity;
import com.aquasuite.ims.module.inventory.api.dto.SkuReservationStock;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class StockReservationStore {
    private final JdbcTemplate jdbc;
    public List<SkuReservationStock> stockByVariant(UUID variantId) {
        return jdbc.query("""
                select variant_id,warehouse_code,on_hand_qty,reserved_qty,
                       greatest(on_hand_qty-reserved_qty,0) as available_qty
                from public.inventories where variant_id=? order by warehouse_code
                """, (rs,n) -> new SkuReservationStock(rs.getObject("variant_id",UUID.class),
                rs.getString("warehouse_code"),rs.getBigDecimal("on_hand_qty"),
                rs.getBigDecimal("reserved_qty"),rs.getBigDecimal("available_qty")),variantId);
    }
    /** UC36 owns its ordered locks; no changes to the shared inventory repository are needed. */
    public List<UUID> lockInventories(UUID variantId) {
        return jdbc.query("select inventory_id from public.inventories where variant_id=? order by inventory_id for update",
                (rs,n) -> rs.getObject(1,UUID.class),variantId);
    }
    public boolean reconciled(UUID variantId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select not exists (
                  select 1 from public.inventories i
                  where i.variant_id=? and i.reserved_qty <>
                    (select coalesce(sum(r.held_qty),0) from public.stock_reservations r where r.inventory_id=i.inventory_id)
                )
                """,Boolean.class,variantId));
    }
    /** A positive difference belongs to other workflows; never claim or free it here. */
    public boolean coversRecordedHolds(UUID variantId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select not exists (
                  select 1 from public.inventories i
                  where i.variant_id=? and (i.reserved_qty < 0 or
                    i.reserved_qty < (select coalesce(sum(r.held_qty),0)
                      from public.stock_reservations r where r.inventory_id=i.inventory_id))
                )
                """, Boolean.class, variantId));
    }
    private static final String SELECT = """
            select r.*, greatest(requested_qty - sum(held_qty+consumed_qty+released_qty)
              over(partition by source_type,source_id,source_line_id), 0) as waiting_qty
            from public.stock_reservations r
            """;
    private static final RowMapper<Reservation> ROW = (rs, n) -> new Reservation(
            rs.getObject("reservation_id", UUID.class), rs.getObject("source_id", UUID.class),
            rs.getObject("source_line_id", UUID.class), rs.getObject("variant_id", UUID.class),
            rs.getString("warehouse_code"), rs.getBigDecimal("requested_qty"), rs.getBigDecimal("held_qty"),
            rs.getBigDecimal("consumed_qty"), rs.getBigDecimal("released_qty"), rs.getBigDecimal("waiting_qty"));

    // Transaction-scoped locks serialize retry keys and source lines even before rows exist.
    public void lock(String key) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?, 0))", rs -> {}, key);
    }
    public List<String> operation(UUID id) {
        return jdbc.query("select payload, result::text from public.stock_reservation_operations where request_id = ?",
                (rs, n) -> List.of(rs.getString(1), rs.getString(2)), id).stream().findFirst().orElse(List.of());
    }
    public List<Reservation> line(UUID order, UUID item) {
        return jdbc.query(SELECT + " where source_type='ORDER' and source_id=? and source_line_id=? order by reservation_id",
                ROW, order, item);
    }
    public Reservation find(UUID id) {
        return jdbc.query("select * from (" + SELECT + ") balances where reservation_id=?", ROW, id)
                .stream().findFirst().orElse(null);
    }
    public List<Reservation> order(UUID id) {
        return jdbc.query(SELECT + " where source_type='ORDER' and source_id=? order by source_line_id, warehouse_code",
                ROW, id);
    }
    public void insert(Reservation r, UUID inventoryId) {
        jdbc.update("""
                insert into public.stock_reservations(reservation_id, inventory_id, source_type, source_id,
                  source_line_id, variant_id, warehouse_code, requested_qty, held_qty, consumed_qty, released_qty)
                values(?,?,'ORDER',?,?,?,?,?,?,?,?)
                """, r.reservationId(), inventoryId, r.orderId(), r.orderItemId(), r.variantId(),
                r.warehouseCode(), r.requestedQty(), r.heldQty(), r.consumedQty(), r.releasedQty());
    }
    public void quantities(Reservation r) {
        jdbc.update("update public.stock_reservations set held_qty=?, released_qty=?, updated_at=current_timestamp where reservation_id=?",
                r.heldQty(), r.releasedQty(), r.reservationId());
    }
    public void record(UUID request, Reservation r, String action, String payload, String result,
                       BigDecimal before, UUID actor, String reason) {
        jdbc.update("""
                insert into public.stock_reservation_operations(request_id,reservation_id,action,payload,result,
                held_before,held_after,actor_id,reason) values(?,?,?,?,?::jsonb,?,?,?,?)
                """, request, r.reservationId(), action, payload, result, before, r.heldQty(), actor, reason);
    }
    public List<Activity> history(UUID id) {
        return jdbc.query("select * from public.stock_reservation_operations where reservation_id=? order by created_at,request_id",
                (rs,n) -> new Activity(rs.getObject("request_id", UUID.class), id, rs.getString("action"),
                        rs.getBigDecimal("held_before"), rs.getBigDecimal("held_after"), rs.getObject("actor_id", UUID.class),
                        rs.getString("reason"), rs.getTimestamp("created_at").toInstant()), id);
    }
}
