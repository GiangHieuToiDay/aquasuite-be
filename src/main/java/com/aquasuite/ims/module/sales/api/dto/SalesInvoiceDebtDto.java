package com.aquasuite.ims.module.sales.api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Thông tin hóa đơn chỉ đọc để Finance tính công nợ theo hóa đơn.
 * @param invoiceId mã hóa đơn
 * @param orderId mã đơn liên quan
 * @param customerId mã khách hàng
 * @param invoiceCode mã hóa đơn hiển thị
 * @param invoiceSymbol ký hiệu hóa đơn nếu có
 * @param issueDate thời điểm phát hành được lưu trên hóa đơn
 * @param signedAt thời điểm ký nếu có
 * @param status trạng thái hóa đơn
 * @param subtotal tiền trước chiết khấu và thuế
 * @param discountAmount số tiền chiết khấu
 * @param taxAmount số tiền thuế
 * @param totalAmount tổng tiền hóa đơn
 * @param originalInvoiceId mã hóa đơn gốc nếu có
 * @param invoiceType loại hóa đơn, ví dụ STORE cho hóa đơn cửa hàng
 */
public record SalesInvoiceDebtDto(
        UUID invoiceId,
        UUID orderId,
        UUID customerId,
        String invoiceCode,
        String invoiceSymbol,
        OffsetDateTime issueDate,
        OffsetDateTime signedAt,
        Integer creditTermDays,
        LocalDate dueDate,
        String status,
        BigDecimal subtotal,
        BigDecimal discountAmount,
        BigDecimal taxAmount,
        BigDecimal totalAmount,
        UUID originalInvoiceId,
        String invoiceType) {}
