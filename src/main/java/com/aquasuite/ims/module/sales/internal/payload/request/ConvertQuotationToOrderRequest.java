package com.aquasuite.ims.module.sales.internal.payload.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Payload tùy chọn khi chuyển đổi Báo giá (Quotation) thành Đơn hàng công trình (Sales Order) - UC-28 / UC-073.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertQuotationToOrderRequest {

    /** Ghi chú bổ sung cho đơn hàng (nếu để trống sẽ kế thừa từ ghi chú của báo giá) */
    private String note;

    /** Địa chỉ giao hàng thực tế (nếu để trống sẽ kế thừa từ địa chỉ giao hàng của báo giá) */
    private String deliveryAddress;

    /** Hạn thanh toán / ngày giao hàng dự kiến (nếu để trống sẽ lấy theo hạn hiệu lực của báo giá hoặc +30 ngày) */
    private LocalDate dueDate;

    /** Cờ yêu cầu tạo đề xuất tăng hạn mức công nợ tới Chủ cửa hàng nếu hạn mức khả dụng không đủ */
    private Boolean requestCreditIncrease;

    /** Lý do xin cấp hoặc tăng hạn mức công nợ trình Chủ cửa hàng phê duyệt */
    private String creditIncreaseReason;

    /** Optional opt-in; a positive quotation depositPercent automatically defers holds and selects WH_MAIN. */
    private boolean reserveStockAfterDeposit;
}

