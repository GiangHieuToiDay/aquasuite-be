package com.aquasuite.ims.module.finance.api;

import com.aquasuite.ims.module.finance.api.dto.CreatePaymentTransactionCommand;
import com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto;

import java.util.List;
import java.util.UUID;

/**
 * Public synchronous interface của module Finance (FE-15 / Quản lý tài chính, sổ quỹ).
 * Các module khác (Sales, Inventory, Fulfillment, ...) gọi Finance qua interface này.
 */
public interface FinanceApi {

    /**
     * Ghi nhận giao dịch thu tiền đơn hàng (RECEIPT) vào sổ quỹ kế toán.
     */
    PaymentTransactionResponseDto recordPaymentTransaction(CreatePaymentTransactionCommand command);

    /**
     * Lấy danh sách giao dịch thanh toán theo mã đơn hàng.
     */
    List<PaymentTransactionResponseDto> getTransactionsByOrderId(UUID orderId);
}
