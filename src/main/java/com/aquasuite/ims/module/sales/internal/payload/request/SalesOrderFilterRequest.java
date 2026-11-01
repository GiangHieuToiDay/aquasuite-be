package com.aquasuite.ims.module.sales.internal.payload.request;

import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesOrderFilterRequest {

    /** Từ khóa tìm kiếm theo mã đơn hàng hoặc SĐT khách hàng */
    private String keyword;

    /** Lọc theo trạng thái đơn hàng (PROCESSING, COMPLETED, CANCELLED) */
    private OrderStatus orderStatus;

    /** Lọc theo trạng thái thanh toán (UNPAID, PARTIALLY_PAID, PAID) */
    private PaymentStatus paymentStatus;

    /** Lọc theo loại đơn hàng (RETAIL, PROJECT) */
    private OrderType orderType;

    /** Lọc theo khoảng ngày tạo đơn (từ ngày) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    /** Lọc theo khoảng ngày tạo đơn (đến ngày) */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
