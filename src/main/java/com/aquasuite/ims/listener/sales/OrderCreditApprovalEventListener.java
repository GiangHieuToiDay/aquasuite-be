package com.aquasuite.ims.listener.sales;

import com.aquasuite.ims.event.crm.CustomerCreditProfileRequestEvent;
import com.aquasuite.ims.service.finance.CreditControlService;
import com.aquasuite.ims.dto.finance.command.CreditAuthorizationCommand;
import com.aquasuite.ims.dto.finance.response.CreditAuthorizationDecision;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.event.sales.OrderConfirmedEvent;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.enums.sales.OrderChannel;
import com.aquasuite.ims.enums.sales.OrderStatus;
import com.aquasuite.ims.repository.sales.OrderItemRepository;
import com.aquasuite.ims.repository.sales.OrderRepository;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Lắng nghe sự kiện duyệt hoặc từ chối yêu cầu cấp hạn mức công nợ từ Chủ cửa hàng (UC-24).
 * Khi Chủ DUYỆT: Kiểm tra hạn mức công nợ khả dụng cho từng Đơn hàng nháp (DRAFT); nếu đủ và khách
 * không bị khóa thì tự động chuyển sang PROCESSING, ngược lại giữ ở trạng thái DRAFT kèm ghi chú.
 * Khi Chủ TỪ CHỐI: Tự động chuyển Đơn hàng nháp (DRAFT) sang CANCELLED và giải phóng tồn kho.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCreditApprovalEventListener {

    static final String APPROVED_NOTE = "[Hạn mức công nợ đã được Chủ cửa hàng phê duyệt]";
    static final String INSUFFICIENT_CREDIT_NOTE = "[Chờ xử lý: Giá trị đơn vượt hạn mức công nợ khả dụng vừa cấp]";
    static final String REJECTED_NOTE = "[Bị từ chối: Chủ cửa hàng không cấp hạn mức công nợ]";

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final InventoryService inventoryApi;
    private final CreditControlService creditControlApi;
    private final ApplicationEventPublisher eventPublisher;

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
            Map<UUID, BigDecimal> remainingCreditByCustomer = new HashMap<>();
            List<Order> orderedDrafts = relatedOrders.stream()
                    .sorted(Comparator.comparing(Order::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();

            for (Order order : orderedDrafts) {
                if (OrderStatus.DRAFT.name().equalsIgnoreCase(order.getOrderStatus())) {
                    BigDecimal requiredDebt = resolveOrderDebtAmount(order);
                    String channel = resolveOrderChannel(order);
                    boolean creditAllowed = isOrderCreditAllowed(order, channel, requiredDebt, remainingCreditByCustomer);

                    if (creditAllowed) {
                        order.setOrderStatus(OrderStatus.PROCESSING.name());
                        order.setUpdatedAt(OffsetDateTime.now());
                        order.setNote(appendNote(order.getNote(), APPROVED_NOTE));
                        orderRepository.save(order);
                        log.info("Order {} transitioned from DRAFT to PROCESSING upon Shop Owner credit approval (requestId={})",
                                order.getOrderCode(), event.requestId());

                        // Đơn dự án vừa được xác nhận (PROCESSING) -> phát sự kiện để Servicedesk sinh công việc lắp đặt (FE-13).
                        eventPublisher.publishEvent(new OrderConfirmedEvent(
                                order.getOrderId(),
                                order.getOrderCode(),
                                order.getCustomerId(),
                                order.getOrderType(),
                                order.getSalesStaffId(),
                                order.getDeliveryAddress(),
                                order.getProjectName()));
                    } else {
                        order.setOrderStatus(OrderStatus.DRAFT.name());
                        order.setUpdatedAt(OffsetDateTime.now());
                        order.setNote(appendNoteOnce(order.getNote(), INSUFFICIENT_CREDIT_NOTE));
                        orderRepository.save(order);
                        log.warn("Order {} kept in DRAFT because available credit or customer status did not satisfy order debt {} (requestId={})",
                                order.getOrderCode(), requiredDebt, event.requestId());
                    }
                }
            }
        } else if ("REJECTED".equalsIgnoreCase(event.action())) {
            for (Order order : relatedOrders) {
                if (OrderStatus.DRAFT.name().equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus(OrderStatus.CANCELLED.name());
                    order.setUpdatedAt(OffsetDateTime.now());
                    order.setNote(appendNote(order.getNote(), REJECTED_NOTE));
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

    private boolean isOrderCreditAllowed(
            Order order,
            String channel,
            BigDecimal requiredDebt,
            Map<UUID, BigDecimal> remainingCreditByCustomer) {
        if (order.getCustomerId() == null) {
            return false;
        }
        CreditAuthorizationDecision decision;
        try {
            decision = creditControlApi.authorize(new CreditAuthorizationCommand(
                    order.getCustomerId(),
                    order.getOrderId(),
                    channel,
                    requiredDebt));
        } catch (AppException ex) {
            log.warn("Credit authorization failed for draft order {}: {}", order.getOrderCode(), ex.getMessage());
            return false;
        }

        if (decision == null || !decision.approved()) {
            return false;
        }

        BigDecimal reportedAvailable = decision.availableCredit();
        if (reportedAvailable == null) {
            return true;
        }

        BigDecimal trackedRemaining = remainingCreditByCustomer.get(order.getCustomerId());
        BigDecimal effectiveAvailable = trackedRemaining == null
                ? reportedAvailable
                : trackedRemaining.min(reportedAvailable);

        if (effectiveAvailable.compareTo(requiredDebt) < 0) {
            return false;
        }

        remainingCreditByCustomer.put(
                order.getCustomerId(),
                effectiveAvailable.subtract(requiredDebt).max(BigDecimal.ZERO));
        return true;
    }

    private BigDecimal resolveOrderDebtAmount(Order order) {
        if (order.getRemainingDebtAmount() != null
                && order.getRemainingDebtAmount().compareTo(BigDecimal.ZERO) > 0) {
            return order.getRemainingDebtAmount();
        }
        BigDecimal totalAmount = order.getTotalAmount() != null ? order.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal depositAmount = order.getDepositAmount();
        if (depositAmount == null
                && order.getDepositPercent() != null
                && order.getDepositPercent().compareTo(BigDecimal.ZERO) > 0) {
            depositAmount = totalAmount.multiply(order.getDepositPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        }
        if (depositAmount != null && depositAmount.compareTo(BigDecimal.ZERO) > 0) {
            return totalAmount.subtract(depositAmount).max(BigDecimal.ZERO);
        }
        return totalAmount.max(BigDecimal.ZERO);
    }

    private String resolveOrderChannel(Order order) {
        String channel = order.getOrderChannel();
        if (channel == null || channel.isBlank()) {
            return OrderChannel.DIRECT_SALES.name();
        }
        return OrderChannel.POS.name().equalsIgnoreCase(channel)
                ? OrderChannel.COUNTER.name()
                : channel;
    }

    private String appendNote(String existingNote, String suffix) {
        return (existingNote != null && !existingNote.isBlank() ? existingNote + " " : "") + suffix;
    }

    private String appendNoteOnce(String existingNote, String suffix) {
        if (existingNote != null && existingNote.contains(suffix)) {
            return existingNote;
        }
        return appendNote(existingNote, suffix);
    }
}
