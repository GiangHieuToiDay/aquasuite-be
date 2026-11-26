package com.aquasuite.ims.dto.sales.request;

import com.aquasuite.ims.enums.sales.QuotationStatus;
import com.aquasuite.ims.common.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class QuotationFilterRequest extends PageQuery {

    /**
     * Tìm kiếm theo mã báo giá (quotationCode) hoặc tên công trình (projectName).
     */
    @Size(max = 100, message = "Từ khóa tìm kiếm tối đa 100 ký tự")
    private String search;

    /**
     * Lọc theo trạng thái báo giá (ví dụ: PENDING_APPROVAL, SENT, DRAFT...).
     */
    private QuotationStatus status;

    /**
     * Lọc theo khách hàng cụ thể.
     */
    private UUID customerId;

    /**
     * Lọc theo khoảng ngày lập báo giá (từ ngày).
     */
    private LocalDate fromDate;

    /**
     * Lọc theo khoảng ngày lập báo giá (đến ngày).
     */
    private LocalDate toDate;
}
