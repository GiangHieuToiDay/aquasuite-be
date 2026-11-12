package com.aquasuite.ims.module.sales.internal.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.Subselect;
import org.hibernate.annotations.Synchronize;

import java.util.UUID;

/** Read-only candidates; Finance decides verified deposit satisfaction via its public API. */
@Entity
@Immutable
@Synchronize({"orders", "order_payment_milestones"})
@Subselect("""
        select o.order_id
        from public.orders o
        where o.order_status in ('CONFIRMED', 'PROCESSING')
          and (o.deposit_percent > 0 or o.deposit_amount > 0 or exists (
              select 1 from public.order_payment_milestones m where m.order_id = o.order_id))
        """)
public class DepositReservationOrder {
    @Id
    private UUID orderId;
}
