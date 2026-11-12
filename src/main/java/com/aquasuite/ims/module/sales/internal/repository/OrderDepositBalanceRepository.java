package com.aquasuite.ims.module.sales.internal.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/** UC-29 read projection for its additive paid_amount column.
 * Keeps the upstream Order entity and SalesOrderApi contracts unchanged.
 * No receipt or arbitrary balance mutation is exposed here.
 */
@Repository
@RequiredArgsConstructor
public class OrderDepositBalanceRepository {
    private final JdbcTemplate jdbc;

    @Transactional(readOnly = true)
    public Optional<Balance> findByOrderId(UUID orderId) {
        if (orderId == null) return Optional.empty();
        return jdbc.query("select order_id,total_amount,paid_amount from public.orders where order_id=?",
                (rs,n) -> new Balance(rs.getObject("order_id", UUID.class),
                        rs.getBigDecimal("total_amount"), rs.getBigDecimal("paid_amount")), orderId)
                .stream().findFirst();
    }

    public record Balance(UUID orderId, BigDecimal totalAmount, BigDecimal paidAmount) {
        public BigDecimal outstandingAmount() { return totalAmount.subtract(paidAmount).max(BigDecimal.ZERO); }
        public String derivedPaymentStatus() {
            if (paidAmount.signum() == 0) return "UNPAID";
            return paidAmount.compareTo(totalAmount) < 0 ? "PARTIALLY_PAID" : "PAID";
        }
    }
}
