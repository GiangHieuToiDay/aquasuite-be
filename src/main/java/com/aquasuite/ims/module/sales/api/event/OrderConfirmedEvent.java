package com.aquasuite.ims.module.sales.api.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Sự kiện phát ra khi một đơn hàng được xác nhận (chuyển sang trạng thái PROCESSING).
 * <p>Là một "sự thật nghiệp vụ" trung lập của module Sales; các module khác tự quyết định phản ứng.
 * Module Servicedesk (FE-13) lắng nghe sự kiện này: với đơn loại {@code PROJECT} (đơn công trình có
 * lắp đặt kỹ thuật) sẽ tự sinh một công việc kỹ thuật loại lắp đặt (INSTALLATION) ở trạng thái
 * ASSIGNED, chờ Chủ cửa hàng phân công kỹ thuật viên.</p>
 *
 * @param eventId        khóa idempotent cho phía tiêu thụ
 * @param orderId        mã đơn hàng
 * @param orderCode      mã hiển thị của đơn hàng
 * @param customerId     mã khách hàng
 * @param orderType      loại đơn (RETAIL | PROJECT)
 * @param salesStaffId   nhân viên bán hàng lập đơn (có thể null)
 * @param deliveryAddress địa chỉ giao/lắp đặt (có thể null)
 * @param projectName    tên công trình/dự án (có thể null)
 * @param occurredAt     thời điểm xác nhận
 */
public record OrderConfirmedEvent(
        UUID eventId,
        UUID orderId,
        String orderCode,
        UUID customerId,
        String orderType,
        UUID salesStaffId,
        String deliveryAddress,
        String projectName,
        Instant occurredAt) {

    public OrderConfirmedEvent(UUID orderId, String orderCode, UUID customerId, String orderType,
                               UUID salesStaffId, String deliveryAddress, String projectName) {
        this(UUID.randomUUID(), orderId, orderCode, customerId, orderType,
                salesStaffId, deliveryAddress, projectName, Instant.now());
    }
}
