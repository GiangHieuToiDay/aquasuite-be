package com.aquasuite.ims.module.sales.internal.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Durable work is committed with the verified payment, not with an in-memory listener. */
@Repository
@RequiredArgsConstructor
public class ReservationAllocationStore {
    private final JdbcTemplate jdbc;

    public void enqueue(UUID id, UUID order) {
        jdbc.update("""
                insert into public.sales_reservation_allocations(allocation_id,order_id)
                select ?,r.order_id from public.sales_reservation_requests r where r.order_id=?
                on conflict(allocation_id) do nothing
                """,id,order);
    }
    public Optional<Job> find(UUID id) {
        return jdbc.query("select allocation_id,order_id,status from public.sales_reservation_allocations where allocation_id=?",
                (rs,n) -> new Job(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3)),id).stream().findFirst();
    }
    public Optional<Job> lock(UUID id) {
        return jdbc.query("select allocation_id,order_id,status from public.sales_reservation_allocations where allocation_id=? for update skip locked",
                (rs,n) -> new Job(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),rs.getString(3)),id).stream().findFirst();
    }
    public void done(UUID id) {
        jdbc.update("update public.sales_reservation_allocations set status='DONE',attempts=attempts+1,error_code=null,updated_at=now() where allocation_id=?",id);
    }
    @Transactional(propagation=Propagation.REQUIRES_NEW)
    public void failed(UUID id, String errorCode) {
        jdbc.update("""
                update public.sales_reservation_allocations
                set status='FAILED',attempts=attempts+1,error_code=?,next_attempt_at=now()+interval '30 seconds',updated_at=now()
                where allocation_id=? and status <> 'DONE'
                """,errorCode,id);
    }
    public List<UUID> due() {
        return jdbc.query("select allocation_id from public.sales_reservation_allocations where status in ('PENDING','FAILED') and next_attempt_at<=now() order by next_attempt_at,allocation_id limit 50",
                (rs,n) -> rs.getObject(1,UUID.class));
    }
    public record Job(UUID id, UUID orderId, String status) {}
}
