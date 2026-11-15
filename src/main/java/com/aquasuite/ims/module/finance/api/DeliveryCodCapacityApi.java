package com.aquasuite.ims.module.finance.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Caller holds the Sales order lock. Amounts exclude declared cash per delivery exactly once. */
public interface DeliveryCodCapacityApi {
    BigDecimal available(UUID orderId, List<Commitment> commitments);
    record Commitment(UUID deliveryId, BigDecimal amount) {}
}
