package com.aquasuite.ims.module.sales.api;

import java.util.UUID;

/** Public read contract used by CRM to evaluate customer credit trust rules. */
public interface CustomerOrderHistoryApi {
    long countQualifiedOrders(UUID customerId);
}
