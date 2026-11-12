package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectDepositOrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderIdOrderByOrderItemId(UUID orderId);
}
