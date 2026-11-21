package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.entity.sales.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ProjectDepositOrderRepository extends JpaRepository<Order, UUID> {}
