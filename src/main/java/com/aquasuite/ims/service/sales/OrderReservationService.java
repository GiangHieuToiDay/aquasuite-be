package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;

import java.util.List;
import java.util.UUID;

/**
 * Service quản lý giữ hàng tồn kho và giải phóng giữ hàng hợp nhất.
 */
public interface OrderReservationService {
    void registerConvertedOrder(Order order, List<OrderItem> source, UUID actorId);
    OrderStockReservationView get(UUID orderId);
    OrderStockReservationView apply(UUID orderId, OrderStockReservationRequest request, boolean isSynchronize);
    ReservationManagementView history(UUID orderId);
    ReservationManagementView.ReleaseResult release(UUID orderId, UUID requestId, String reason);
}
