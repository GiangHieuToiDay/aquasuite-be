package com.aquasuite.ims.service.sales;

import java.util.UUID;

/** Public read contract used by CRM to evaluate customer credit trust rules. */
public interface CustomerOrderHistoryService {
    long countQualifiedOrders(UUID customerId);
}
