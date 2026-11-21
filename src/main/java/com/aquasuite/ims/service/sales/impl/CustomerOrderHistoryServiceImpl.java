package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.service.sales.CustomerOrderHistoryService;
import com.aquasuite.ims.repository.sales.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CustomerOrderHistoryServiceImpl implements CustomerOrderHistoryService {
    private final OrderRepository orderRepository;

    @Override
    public long countQualifiedOrders(UUID customerId) {
        if (customerId == null) return 0;
        return orderRepository.countByCustomerIdAndOrderStatusAndPaymentStatus(customerId, "COMPLETED", "PAID");
    }
}
