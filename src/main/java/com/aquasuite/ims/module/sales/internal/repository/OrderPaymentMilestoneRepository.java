package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.OrderPaymentMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OrderPaymentMilestoneRepository extends JpaRepository<OrderPaymentMilestone, UUID> {

    List<OrderPaymentMilestone> findByOrderIdOrderBySortOrderAsc(UUID orderId);

    void deleteByOrderId(UUID orderId);
}

