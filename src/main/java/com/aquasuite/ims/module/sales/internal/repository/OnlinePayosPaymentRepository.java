package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.OnlinePayosPayment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface OnlinePayosPaymentRepository extends JpaRepository<OnlinePayosPayment, UUID> {

    Optional<OnlinePayosPayment> findByOrderId(UUID orderId);

    boolean existsByPayosOrderCode(Long payosOrderCode);
}

