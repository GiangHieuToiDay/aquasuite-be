package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.entity.sales.OrderModificationRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderModificationRequestItemRepository extends JpaRepository<OrderModificationRequestItem, UUID> {
    List<OrderModificationRequestItem> findByModificationRequestIdOrderByCreatedAtAsc(UUID modificationRequestId);
}
