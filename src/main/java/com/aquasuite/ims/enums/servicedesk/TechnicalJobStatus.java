package com.aquasuite.ims.enums.servicedesk;

/**
 * Vòng đời công việc kỹ thuật (FE-13, UC-59/60/61).
 * <pre>
 *   ASSIGNED ──accept (UC-60)──► IN_PROGRESS ──complete (UC-61)──► COMPLETED
 *      │                            │
 *      ├──reject (UC-60)──► REJECTED │
 *      │                     │       │
 *      └──────── cancel (Chủ cửa hàng / đơn hàng bị hủy) ──────────► CANCELLED
 * </pre>
 * <ul>
 *   <li>{@link #ASSIGNED}: job đã được tạo (có thể chưa gán kỹ thuật viên), chờ tiếp nhận.</li>
 *   <li>{@link #IN_PROGRESS}: kỹ thuật viên đã tiếp nhận và đang thực hiện.</li>
 *   <li>{@link #COMPLETED}: đã ghi kết quả và hoàn tất (kết thúc).</li>
 *   <li>{@link #REJECTED}: kỹ thuật viên từ chối nhận việc; Chủ cửa hàng gán lại (về ASSIGNED) hoặc hủy.</li>
 *   <li>{@link #CANCELLED}: Chủ cửa hàng hủy job, hoặc hệ thống hủy vì đơn hàng nguồn bị hủy (kết thúc).</li>
 * </ul>
 * <p>Việc gán lại (về ASSIGNED) do {@code assignJob} xử lý riêng, không đi qua {@link #canTransitionTo}.</p>
 */
public enum TechnicalJobStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED,
    CANCELLED;

    /** Chỉ cho phép chuyển trạng thái theo đúng luồng nghiệp vụ đã chốt. */
    public boolean canTransitionTo(TechnicalJobStatus target) {
        return switch (this) {
            case ASSIGNED -> target == IN_PROGRESS || target == REJECTED || target == CANCELLED;
            case IN_PROGRESS -> target == COMPLETED || target == CANCELLED;
            case REJECTED -> target == CANCELLED;
            case COMPLETED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED || this == CANCELLED;
    }

    /** Job còn hiệu lực, có thể gán lại hoặc hủy (chưa hoàn tất, chưa hủy). */
    public boolean isOpen() {
        return this == ASSIGNED || this == IN_PROGRESS || this == REJECTED;
    }
}
