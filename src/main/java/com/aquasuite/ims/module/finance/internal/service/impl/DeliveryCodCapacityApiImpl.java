package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.api.DeliveryCodCapacityApi;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.share.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DeliveryCodCapacityApiImpl implements DeliveryCodCapacityApi {
    private final TransactionRepository transactions;
    private final OrderDispatchApi sales;

    @Transactional(propagation = Propagation.MANDATORY)
    public BigDecimal available(UUID orderId, List<Commitment> commitments) {
        var order = sales.lock(orderId);
        var receipts = transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT");
        BigDecimal paid = BigDecimal.ZERO, pending = BigDecimal.ZERO, promised = BigDecimal.ZERO;
        Map<UUID, BigDecimal> declared = new HashMap<>();
        for (var t : receipts) {
            if ("VERIFIED".equals(t.getStatus())) paid = paid.add(t.getAmount());
            if ("COD".equals(t.getPaymentType()) && "CASH".equals(t.getPaymentMethod())
                    && List.of("PENDING", "VERIFIED").contains(t.getStatus())) {
                if ("PENDING".equals(t.getStatus())) pending = pending.add(t.getAmount());
                if (t.getDeliveryId() != null) declared.merge(t.getDeliveryId(), t.getAmount(), BigDecimal::add);
            }
        }
        for (var c : commitments) promised = promised.add(c.amount()
                .subtract(declared.getOrDefault(c.deliveryId(), BigDecimal.ZERO)).max(BigDecimal.ZERO));
        return order.totalAmount().subtract(paid).subtract(pending).subtract(promised).max(BigDecimal.ZERO);
    }
}
