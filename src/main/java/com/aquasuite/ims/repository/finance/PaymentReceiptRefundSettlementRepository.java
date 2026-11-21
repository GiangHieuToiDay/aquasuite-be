package com.aquasuite.ims.repository.finance;

import com.aquasuite.ims.entity.finance.PaymentReceiptRefundSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/** Finance-only ledger for verified refunds that consume original receipt balances. */
public interface PaymentReceiptRefundSettlementRepository extends JpaRepository<PaymentReceiptRefundSettlement, UUID> {
    List<PaymentReceiptRefundSettlement> findByReceiptTransactionIdIn(Collection<UUID> receiptTransactionIds);

    @Query("select coalesce(sum(s.settledAmount), 0) from PaymentReceiptRefundSettlement s "
            + "where s.receiptTransactionId = :receiptId")
    BigDecimal sumByReceiptTransactionId(@Param("receiptId") UUID receiptId);

    @Query("select coalesce(sum(s.settledAmount), 0) from PaymentReceiptRefundSettlement s "
            + "where s.refundTransactionId = :refundId")
    BigDecimal sumByRefundTransactionId(@Param("refundId") UUID refundId);
}
