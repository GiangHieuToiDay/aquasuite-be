package com.aquasuite.ims.service.sales.impl;

import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.service.sales.OrderReservationService;
import com.aquasuite.ims.service.sales.impl.OrderStockReservationService;
import com.aquasuite.ims.service.sales.impl.ReservationManagementService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class OrderReservationServiceImpl implements OrderReservationService {

    private final OrderStockReservationService stockReservationService;
    private final ReservationManagementService reservationManagementService;

    @Override
    public void registerConvertedOrder(Order order, List<OrderItem> source, UUID actorId) {
        stockReservationService.registerConvertedOrder(order, source, actorId);
    }

    @Override
    public OrderStockReservationView get(UUID orderId) {
        return stockReservationService.get(orderId);
    }

    @Override
    public OrderStockReservationView apply(UUID orderId, OrderStockReservationRequest request, boolean isSynchronize) {
        return stockReservationService.apply(orderId, request, isSynchronize);
    }

    @Override
    public ReservationManagementView history(UUID orderId) {
        return reservationManagementService.history(orderId);
    }

    @Override
    public ReservationManagementView.ReleaseResult release(UUID orderId, UUID requestId, String reason) {
        return reservationManagementService.release(orderId, requestId);
    }
}
