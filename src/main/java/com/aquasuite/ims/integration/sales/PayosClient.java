package com.aquasuite.ims.integration.sales;

import java.util.List;

/**
 * Cổng giao tiếp với hệ thống PayOS thông qua SDK chính thức vn.payos:payos-java.
 */
public interface PayosClient {

    PaymentLinkResult createPaymentLink(CreatePaymentCommand command);

    PaymentLinkStatusResult getPaymentLinkInfo(long payosOrderCode);

    PaymentLinkStatusResult cancelPaymentLink(long payosOrderCode, String reason);

    record PaymentItemData(String name, int quantity, long price, String unit) {}

    record CreatePaymentCommand(
            long orderCode,
            long amount,
            String description,
            String returnUrl,
            String cancelUrl,
            String buyerName,
            String buyerEmail,
            String buyerPhone,
            String buyerAddress,
            List<PaymentItemData> items
    ) {}

    record PaymentLinkResult(
            long orderCode,
            String paymentLinkId,
            String checkoutUrl,
            String qrCode,
            String bin,
            String accountNumber,
            String accountName,
            long amount,
            String description,
            String status
    ) {}

    record PaymentLinkStatusResult(
            long orderCode,
            String paymentLinkId,
            long amount,
            long amountPaid,
            long amountRemaining,
            String status
    ) {}
}

