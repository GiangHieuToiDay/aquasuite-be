package com.aquasuite.ims.module.sales.internal.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class OrderStockReservationStore {
    private final JdbcTemplate jdbc;
    public boolean requiresReservationReview(UUID order) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from public.order_modification_requests
                where order_id=? and request_status in ('PENDING_CANCEL','APPROVED')
                and request_type in ('FULL_CANCELLATION','ITEM_CHANGE'))
                """, Boolean.class, order));
    }
    public void lockOrder(UUID order) {
        jdbc.query("select order_id from public.orders where order_id=? for update", rs -> {}, order);
    }
    public void lockRequest(UUID request) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, "uc29:" + request);
    }
    public Optional<Selection> selection(UUID order) {
        return jdbc.query("select selection,pricing,reservation_value,warehouse_code,actor_id from public.sales_reservation_requests where order_id=?",
                (rs,n) -> new Selection(rs.getString(1),rs.getString(2),rs.getBigDecimal(3),rs.getString(4),rs.getObject(5,UUID.class)), order).stream().findFirst();
    }
    public void select(UUID order, String selection, String pricing, BigDecimal value, UUID actor) {
        jdbc.update("insert into public.sales_reservation_requests(order_id,selection,pricing,reservation_value,actor_id) values(?,?,?,?,?)",
                order,selection,pricing,value,actor);
    }
    public void warehouse(UUID order, String warehouse) {
        jdbc.update("update public.sales_reservation_requests set warehouse_code=? where order_id=? and warehouse_code is null",warehouse,order);
    }
    public Optional<String> operation(UUID request) {
        return jdbc.query("select payload from public.sales_reservation_operations where request_id=?",
                (rs,n) -> rs.getString(1),request).stream().findFirst();
    }
    public void record(UUID request, UUID order, String payload, UUID actor) {
        jdbc.update("insert into public.sales_reservation_operations(request_id,order_id,payload,actor_id) values(?,?,?,?)",
                request,order,payload,actor);
    }
    public Optional<Allocation> latestAllocation(UUID order) {
        return jdbc.query("select allocation_id,status,error_code from public.sales_reservation_allocations where order_id=? order by created_at desc,allocation_id desc limit 1",
                (rs,n) -> new Allocation(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),order).stream().findFirst();
    }
    public void clearFailures(UUID order) {
        jdbc.update("update public.sales_reservation_allocations set status='DONE',error_code=null,updated_at=now() where order_id=? and status='FAILED'",order);
    }
    public record Allocation(UUID allocationId, String status, String errorCode) {}
    public record Selection(String lines, String pricing, BigDecimal value, String warehouseCode, UUID actorId) {}
}
