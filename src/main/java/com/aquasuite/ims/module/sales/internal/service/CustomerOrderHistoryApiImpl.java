package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.api.CustomerOrderHistoryApi;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderHistoryApiImpl implements CustomerOrderHistoryApi {
    private final OrderRepository orderRepository;

    @Override
    public long countQualifiedOrders(UUID customerId) {
        if (customerId == null) return 0;
        return orderRepository.countByCustomerIdAndOrderStatusAndPaymentStatus(customerId, "COMPLETED", "PAID");
    }
}
