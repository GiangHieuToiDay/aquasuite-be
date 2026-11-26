package com.aquasuite.ims.dto.inventory.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
 * <p>{@code idempotencyKey} do client sinh một lần cho mỗi lần lập phiếu và gửi lại nguyên vẹn khi
 * retry (ví dụ sau timeout). Cùng khóa + cùng nội dung trả lại phiếu đã ghi, không cộng tồn lần hai;
 * cùng khóa + nội dung khác bị từ chối {@code INV_019} (409).</p>
 *
 * @param idempotencyKey    mã duy nhất cho lần lập phiếu; dùng lại khi gửi lại cùng yêu cầu
 * @param purchaseOrderId   đơn mua hàng cần nhập (phải ở CONFIRMED hoặc PARTIALLY_RECEIVED)
 * @param supplierInvoiceNo số hóa đơn nhà cung cấp (tùy chọn)
 * @param invoiceImageUrl   ảnh hóa đơn (tùy chọn)
 * @param note              ghi chú phiếu (tùy chọn)
 * @param items             các dòng số lượng thực nhận; phải có ít nhất một dòng
 */
@Builder
public record CreateGoodsReceiptRequest(
        @NotNull(message = "idempotencyKey không được để trống")
        UUID idempotencyKey,

        @NotNull(message = "purchaseOrderId không được để trống")
        UUID purchaseOrderId,

        @Size(max = 100, message = "Số hóa đơn nhà cung cấp không được vượt quá 100 ký tự")
        String supplierInvoiceNo,

        @Size(max = 2048, message = "URL ảnh hóa đơn không được vượt quá 2048 ký tự")
        @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "URL ảnh hóa đơn phải bắt đầu bằng http:// hoặc https://")
        String invoiceImageUrl,

        @Size(max = 1000, message = "Ghi chú không được vượt quá 1000 ký tự")
        String note,

        @NotEmpty(message = "Phiếu nhập phải có ít nhất một dòng hàng")
        @Size(max = 500, message = "Tối đa 500 dòng hàng trong một phiếu nhập")
        List<@NotNull(message = "Dòng hàng không được để trống") @Valid CreateGoodsReceiptItemRequest> items
) {}
