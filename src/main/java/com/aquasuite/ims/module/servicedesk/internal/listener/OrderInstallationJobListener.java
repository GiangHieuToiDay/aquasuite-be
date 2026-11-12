package com.aquasuite.ims.module.servicedesk.internal.listener;

import com.aquasuite.ims.module.sales.api.event.OrderConfirmedEvent;
import com.aquasuite.ims.module.servicedesk.internal.entity.TechnicalJob;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.repository.TechnicalJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.util.StringUtils;

import java.util.UUID;

/**
 * Giai đoạn 2 (auto từ Order): khi một đơn hàng được xác nhận, nếu là đơn công trình/dự án
 * ({@code PROJECT}) thì tự sinh một công việc kỹ thuật loại lắp đặt (INSTALLATION) ở trạng thái
 * ASSIGNED, chưa phân công kỹ thuật viên (Chủ cửa hàng sẽ phân công sau — ngoài phạm vi UC-59/60/61).
 * <p>Chạy sau khi giao dịch tạo đơn commit và trong transaction riêng để lỗi sinh job không ảnh hưởng
 * việc tạo đơn hàng. Idempotent theo (orderId, INSTALLATION) nên một đơn chỉ sinh tối đa một job lắp đặt.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInstallationJobListener {

    private static final String PROJECT_ORDER_TYPE = "PROJECT";
    private static final String DEFAULT_ADDRESS = "(Chưa có địa chỉ lắp đặt)";

    private final TechnicalJobRepository technicalJobRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onOrderConfirmed(OrderConfirmedEvent event) {
        if (event == null || event.orderId() == null) {
            return;
        }
        if (!PROJECT_ORDER_TYPE.equalsIgnoreCase(event.orderType())) {
            return; // Đơn bán lẻ không phát sinh công việc lắp đặt.
        }
        UUID createdBy = event.salesStaffId();
        if (createdBy == null) {
            log.warn("Bỏ qua auto-sinh công việc lắp đặt cho đơn {}: thiếu salesStaffId để ghi người tạo",
                    event.orderCode());
            return;
        }
        try {
            if (technicalJobRepository.existsByOrderIdAndJobType(event.orderId(), TechnicalJobType.INSTALLATION)) {
                return; // Đã sinh trước đó.
            }
            TechnicalJob job = TechnicalJob.builder()
                    .customerId(event.customerId())
                    .orderId(event.orderId())
                    .createdBy(createdBy)
                    .jobType(TechnicalJobType.INSTALLATION)
                    .status(TechnicalJobStatus.ASSIGNED)
                    .serviceAddress(StringUtils.hasText(event.deliveryAddress()) ? event.deliveryAddress() : DEFAULT_ADDRESS)
                    .issueDescription(buildIssueDescription(event))
                    .build();
            technicalJobRepository.save(job);
            log.info("Đã tự sinh công việc lắp đặt cho đơn dự án {} (orderId={})", event.orderCode(), event.orderId());
        } catch (RuntimeException ex) {
            log.error("Lỗi khi auto-sinh công việc lắp đặt cho đơn {} (orderId={})",
                    event.orderCode(), event.orderId(), ex);
        }
    }

    private String buildIssueDescription(OrderConfirmedEvent event) {
        StringBuilder sb = new StringBuilder("Lắp đặt kỹ thuật cho đơn dự án ")
                .append(event.orderCode() != null ? event.orderCode() : event.orderId());
        if (StringUtils.hasText(event.projectName())) {
            sb.append(" - công trình: ").append(event.projectName());
        }
        return sb.toString();
    }
}
