package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.DepositReservationOrder;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

public final class DepositReservationOrderSpecification {
    private DepositReservationOrderSpecification() {}

    public static Specification<Order> eligible() {
        return (root, query, builder) -> {
            var eligible = query.subquery(UUID.class);
            var candidate = eligible.from(DepositReservationOrder.class);
            eligible.select(candidate.get("orderId"));
            return root.get("orderId").in(eligible);
        };
    }
}
