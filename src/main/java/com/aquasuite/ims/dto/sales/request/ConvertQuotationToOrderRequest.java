package com.aquasuite.ims.dto.sales.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.Valid;
import jakarta.validation.constraints.FutureOrPresent;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

/**
 * Payload tùy chọn khi chuyển đổi Báo giá (Quotation) thành Đơn hàng công trình (Sales Order) - UC-28 / UC-073.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConvertQuotationToOrderRequest {

    /** Ghi chú bổ sung cho đơn hàng (nếu để trống sẽ kế thừa từ ghi chú của báo giá) */
    @Size(max = 2000, message = "Ghi chú tối đa 2000 ký tự")
    private String note;

    /** Địa chỉ giao hàng thực tế (nếu để trống sẽ kế thừa từ địa chỉ giao hàng của báo giá) */
    @Size(max = 500, message = "Địa chỉ giao hàng tối đa 500 ký tự")
    private String deliveryAddress;

    /** Hạn thanh toán / ngày giao hàng dự kiến (nếu để trống sẽ lấy theo hạn hiệu lực của báo giá hoặc +30 ngày) */
    @FutureOrPresent(message = "Hạn thanh toán / ngày giao dự kiến không được ở trong quá khứ")
    private LocalDate dueDate;

    /** Cờ yêu cầu tạo đề xuất tăng hạn mức công nợ tới Chủ cửa hàng nếu hạn mức khả dụng không đủ */
    private Boolean requestCreditIncrease;

    /** Lý do xin cấp hoặc tăng hạn mức công nợ trình Chủ cửa hàng phê duyệt */
    @Size(max = 1000, message = "Lý do xin tăng hạn mức tối đa 1000 ký tự")
    private String creditIncreaseReason;

    /** Optional opt-in; a positive quotation depositPercent automatically defers holds and selects WH_MAIN. */
    private boolean reserveStockAfterDeposit;

    /** Tùy chọn chia trước các đợt hóa đơn PROJECT (status = DRAFT) ngay khi chuyển báo giá thành đơn hàng. */
    @Size(max = 50, message = "Tối đa 50 đợt hóa đơn khởi tạo")
    private List<@NotNull(message = "Đợt hóa đơn không được để trống") @Valid InitialInvoiceBatchRequest> batches;
}

