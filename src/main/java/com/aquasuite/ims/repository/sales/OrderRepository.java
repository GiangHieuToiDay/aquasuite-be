package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.entity.sales.Order;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
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

    Optional<Order> findByOrderIdAndCustomerId(UUID orderId, UUID customerId);

    boolean existsByOrderCode(String orderCode);

    long countByOrderCodeStartingWith(String prefix);

    boolean existsByQuotationId(UUID quotationId);

    Optional<Order> findByQuotationId(UUID quotationId);

    List<Order> findByQuotationIdIn(Collection<UUID> quotationIds);

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

    List<Order> findByCustomerIdAndOrderChannelOrderByCreatedAtDesc(UUID customerId, String orderChannel);

    List<Order> findByCustomerIdOrderByOrderDateDesc(UUID customerId);

    long countByCustomerIdAndOrderStatusAndPaymentStatus(UUID customerId, String orderStatus, String paymentStatus);

    List<Order> findByCreditRequestId(UUID creditRequestId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select order from Order order where order.orderId = :orderId")
    java.util.Optional<Order> findByIdForUpdate(@Param("orderId") UUID orderId);
}
