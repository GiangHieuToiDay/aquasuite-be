package com.aquasuite.ims.module.servicedesk.internal.enums;

/**
 * Vòng đời công việc kỹ thuật (FE-13, UC-59/60/61).
 * <p>Luồng chuẩn (Giai đoạn 1 - thao tác phía Technical Staff):</p>
 * <pre>
 *   ASSIGNED ──accept (UC-60)──► IN_PROGRESS ──complete (UC-61)──► COMPLETED
 *      │
 *      └──reject (UC-60)──► REJECTED
 * </pre>
 * <ul>
 *   <li>{@link #ASSIGNED}: job đã được tạo và gán cho một kỹ thuật viên, chờ tiếp nhận.</li>
 *   <li>{@link #IN_PROGRESS}: kỹ thuật viên đã tiếp nhận và đang thực hiện.</li>
 *   <li>{@link #COMPLETED}: đã ghi kết quả và hoàn tất (trạng thái kết thúc).</li>
 *   <li>{@link #REJECTED}: kỹ thuật viên từ chối nhận việc (trạng thái kết thúc).</li>
 * </ul>
 */
public enum TechnicalJobStatus {
    ASSIGNED,
    IN_PROGRESS,
    COMPLETED,
    REJECTED;

    /** Chỉ cho phép chuyển trạng thái theo đúng luồng nghiệp vụ đã chốt. */
    public boolean canTransitionTo(TechnicalJobStatus target) {
        return switch (this) {
            case ASSIGNED -> target == IN_PROGRESS || target == REJECTED;
            case IN_PROGRESS -> target == COMPLETED;
            case COMPLETED, REJECTED -> false;
        };
    }

    public boolean isTerminal() {
        return this == COMPLETED || this == REJECTED;
    }
}
