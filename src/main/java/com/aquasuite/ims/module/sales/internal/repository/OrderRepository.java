package com.aquasuite.ims.module.sales.internal.repository;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface OrderRepository extends JpaRepository<Order, UUID>, JpaSpecificationExecutor<Order> {

    /**
     * Khóa dòng đơn đến hết transaction khi Finance ghi nhận thanh toán.
     * @param orderId mã đơn cần khóa
     * @return đơn được khóa nếu tồn tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderId = :orderId")
    Optional<Order> findForPaymentUpdate(@Param("orderId") UUID orderId);

    Optional<Order> findByOrderCode(String orderCode);

    boolean existsByOrderCode(String orderCode);

    long countByOrderCodeStartingWith(String prefix);

    @Query("SELECT COUNT(o) > 0 FROM Order o " +
            "WHERE o.customerId = :customerId " +
            "AND o.paymentStatus != 'PAID' " +
            "AND o.orderStatus != 'CANCELLED' " +
            "AND o.dueDate IS NOT NULL " +
            "AND o.dueDate < :overdueDate")
    boolean hasOverdueUnpaidOrders(
            @Param("customerId") UUID customerId,
            @Param("overdueDate") LocalDate overdueDate
    );
    List<Order> findByCustomerIdIn(Collection<UUID> customerIds);
}
