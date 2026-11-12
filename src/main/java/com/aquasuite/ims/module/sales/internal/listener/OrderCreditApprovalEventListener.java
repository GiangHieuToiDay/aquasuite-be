package com.aquasuite.ims.module.sales.internal.listener;

import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileRequestEvent;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Lắng nghe sự kiện duyệt hoặc từ chối yêu cầu cấp hạn mức công nợ từ Chủ cửa hàng (UC-24).
 * Khi Chủ DUYỆT: Tự động chuyển các Đơn hàng nháp (DRAFT) đang chờ sang PROCESSING.
 * Khi Chủ TỪ CHỐI: Tự động chuyển Đơn hàng nháp (DRAFT) sang CANCELLED và giải phóng tồn kho.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreditApprovalEventListener {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryApi inventoryApi;
    private final org.springframework.context.ApplicationEventPublisher eventPublisher;

    @EventListener
    @Transactional
    public void onCreditProfileRequestEvent(CustomerCreditProfileRequestEvent event) {
        if (event == null || event.requestId() == null) {
            return;
        }

        List<Order> relatedOrders = orderRepository.findByCreditRequestId(event.requestId());
        if (relatedOrders == null || relatedOrders.isEmpty()) {
            return;
        }

        if ("APPROVED".equalsIgnoreCase(event.action())) {
            for (Order order : relatedOrders) {
                if (OrderStatus.DRAFT.name().equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus(OrderStatus.PROCESSING.name());
                    order.setUpdatedAt(OffsetDateTime.now());
                    order.setNote((order.getNote() != null ? order.getNote() + " " : "")
                            + "[Hạn mức công nợ đã được Chủ cửa hàng phê duyệt]");
                    orderRepository.save(order);
                    log.info("Order {} transitioned from DRAFT to PROCESSING upon Shop Owner credit approval (requestId={})",
                            order.getOrderCode(), event.requestId());

                    // Đơn dự án vừa được xác nhận (PROCESSING) -> phát sự kiện để Servicedesk sinh công việc lắp đặt (FE-13).
                    eventPublisher.publishEvent(new com.aquasuite.ims.module.sales.api.event.OrderConfirmedEvent(
                            order.getOrderId(),
                            order.getOrderCode(),
                            order.getCustomerId(),
                            order.getOrderType(),
                            order.getSalesStaffId(),
                            order.getDeliveryAddress(),
                            order.getProjectName()));
                }
            }
        } else if ("REJECTED".equalsIgnoreCase(event.action())) {
            for (Order order : relatedOrders) {
                if (OrderStatus.DRAFT.name().equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus(OrderStatus.CANCELLED.name());
                    order.setUpdatedAt(OffsetDateTime.now());
                    order.setNote((order.getNote() != null ? order.getNote() + " " : "")
                            + "[Bị từ chối: Chủ cửa hàng không cấp hạn mức công nợ]");
                    orderRepository.save(order);

                    // Giải phóng tồn kho đã giữ chỗ cho đơn nháp bị hủy
                    try {
                        List<OrderItem> items = orderItemRepository.findByOrderId(order.getOrderId());
                        for (OrderItem item : items) {
                            if (item.getVariantId() != null && item.getQuantity() != null) {
                                inventoryApi.releaseReservedStock(item.getVariantId(), item.getQuantity());
                            }
                        }
                    } catch (Exception ex) {
                        log.warn("Failed to release stock for cancelled draft order {}: {}", order.getOrderCode(), ex.getMessage());
                    }

                    log.info("Order {} transitioned from DRAFT to CANCELLED upon Shop Owner credit rejection (requestId={})",
                            order.getOrderCode(), event.requestId());
                }
            }
        }
    }
}

