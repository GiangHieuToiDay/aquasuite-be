package com.aquasuite.ims.module.fulfillment.internal.repository;

import com.aquasuite.ims.module.finance.api.DeliveryCodCapacityApi.Commitment;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.*;

@Repository
@RequiredArgsConstructor
public class DeliveryIssueStore {
    private final JdbcTemplate jdbc;

    public void lockRequest(UUID id) {
        jdbc.query("select pg_advisory_xact_lock(hashtextextended(?,0))", rs -> {}, "delivery-create:" + id);
    }
    public UUID orderId(UUID id) {
        return jdbc.query("select order_id from public.deliveries where delivery_id=?",
                (rs,n) -> rs.getObject(1, UUID.class), id).stream().findFirst().orElse(null);
    }
    public int nextBatch(UUID order) {
        return jdbc.queryForObject("select coalesce(max(batch_no),0)+1 from public.deliveries where order_id=?", Integer.class, order);
    }
    public boolean hasAmbiguousLegacy(UUID order) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from public.deliveries where order_id=? and
                  ((confirmed_at is null and delivery_status not in ('DRAFT','CANCELLED'))
                    or (confirmed_at is not null and delivery_status in ('DRAFT','CANCELLED'))))
                """, Boolean.class, order));
    }
    public Map<UUID,BigDecimal> issued(UUID order) {
        Map<UUID,BigDecimal> result = new HashMap<>();
        jdbc.query("""
                select i.order_item_id,sum(i.quantity) from public.delivery_items i
                join public.deliveries d on d.delivery_id=i.delivery_id
                where d.order_id=? and d.confirmed_at is not null group by i.order_item_id
                """, rs -> { result.put(rs.getObject(1,UUID.class),rs.getBigDecimal(2)); }, order);
        return result;
    }
    public Map<UUID,BigDecimal> issuedForInvoice(UUID invoice) {
        Map<UUID,BigDecimal> result=new HashMap<>();
        jdbc.query("select i.order_item_id,sum(i.quantity) from public.delivery_items i join public.deliveries d"
                +" on d.delivery_id=i.delivery_id where d.sales_invoice_id=? and d.confirmed_at is not null group by i.order_item_id",
                rs -> { result.put(rs.getObject(1,UUID.class),rs.getBigDecimal(2)); },invoice);
        return result;
    }
    public List<Commitment> commitments(UUID order) {
        return jdbc.query("select delivery_id,cod_amount from public.deliveries where order_id=?"
                + " and confirmed_at is not null and cod_required=true",
                (rs,n) -> new Commitment(rs.getObject(1,UUID.class),rs.getBigDecimal(2)), order);
    }
}
