package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequestItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OrderModificationRequestItemRepository extends JpaRepository<OrderModificationRequestItem, UUID> {
    List<OrderModificationRequestItem> findByModificationRequestIdOrderByCreatedAtAsc(UUID modificationRequestId);
}
