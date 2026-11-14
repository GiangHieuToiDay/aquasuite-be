package com.aquasuite.ims.module.sales.api;

import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceSummaryDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous interface của module Sales cho phần Hóa đơn bán hàng.
 * Cho phép Fulfillment đọc hóa đơn đã phát hành (status = ISSUED) để lập phiếu xuất kho
 * và thông báo hoàn tất đơn khi tất cả hóa đơn đã xuất kho xong.
 */
public interface SalesInvoiceApi {

    Optional<SalesInvoiceSummaryDto> getInvoiceById(UUID invoiceId);

    List<SalesInvoiceSummaryDto> getInvoicesByOrderId(UUID orderId);

    List<SalesInvoiceSummaryDto> getIssuedInvoicesByOrderId(UUID orderId);

    boolean areAllActiveInvoicesIssued(UUID orderId);

    /**
     * Được gọi sau khi toàn bộ hóa đơn ISSUED của đơn đã được xuất kho xong;
     * cập nhật trạng thái đơn sang COMPLETED nếu thỏa điều kiện thanh toán.
     */
    void completeOrderIfEligibleAfterDispatch(UUID orderId);
}

