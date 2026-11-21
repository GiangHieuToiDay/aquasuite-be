package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.entity.sales.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;

public interface ProjectDepositOrderItemRepository extends JpaRepository<OrderItem, UUID> {
    List<OrderItem> findByOrderIdOrderByOrderItemId(UUID orderId);
}
