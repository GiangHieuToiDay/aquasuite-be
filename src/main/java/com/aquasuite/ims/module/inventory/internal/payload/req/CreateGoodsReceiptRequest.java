package com.aquasuite.ims.module.inventory.internal.payload.req;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.List;
import java.util.UUID;

/**
 * Yêu cầu lập và xác nhận một phiếu nhập kho theo đơn mua hàng (UC-32, Hướng B).
 *
 * <p>Theo quyết định Hướng B, việc tạo và xác nhận phiếu là một hành động atomic: khi request này
 * thành công, tồn kho được cộng ngay và phiếu trở nên bất biến. Mỗi lần nhà cung cấp giao một đợt
 * thực tế có hàng được chấp nhận tương ứng một request; một đơn mua có thể có nhiều phiếu.</p>
 *
 * @param purchaseOrderId   đơn mua hàng cần nhập (phải ở CONFIRMED hoặc PARTIALLY_RECEIVED)
 * @param supplierInvoiceNo số hóa đơn nhà cung cấp (tùy chọn)
 * @param invoiceImageUrl   ảnh hóa đơn (tùy chọn)
 * @param note              ghi chú phiếu (tùy chọn)
 * @param items             các dòng số lượng thực nhận; phải có ít nhất một dòng
 */
@Builder
public record CreateGoodsReceiptRequest(
        @NotNull(message = "purchaseOrderId không được để trống")
        UUID purchaseOrderId,

        String supplierInvoiceNo,

        String invoiceImageUrl,

        String note,

        @NotEmpty(message = "Phiếu nhập phải có ít nhất một dòng hàng")
        List<@Valid CreateGoodsReceiptItemRequest> items
) {}
