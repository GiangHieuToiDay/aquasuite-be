package com.aquasuite.ims.dto.sales.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Thông tin link & mã VietQR PayOS gắn với đơn hàng trực tuyến (UC-18).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayosPaymentLinkResponse {

    private Long payosOrderCode;
    private String paymentLinkId;
    private String checkoutUrl;
    private String qrCode;
    private String bin;
    private String accountNumber;
    private String accountName;
    private Long amount;
    private Long amountPaid;
    private String description;
    private String status;
}

