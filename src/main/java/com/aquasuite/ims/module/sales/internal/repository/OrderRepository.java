package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {
    List<Order> findByCustomerIdIn(Collection<UUID> customerIds);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select order from Order order where order.orderId = :orderId")
    java.util.Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);
}
