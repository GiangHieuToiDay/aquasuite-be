package com.aquasuite.ims.module.purchasing.api;

import com.aquasuite.ims.module.purchasing.api.dto.ReceivablePurchaseOrderView;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Interface của Module Purchasing.
 * Các module khác (Inventory, Catalog, Sales, ...) tương tác với đơn mua hàng qua Interface này.
 */
public interface PurchasingApi {

    /**
     * Lấy số lượng hàng đang đặt (sắp về) cho 1 SKU từ các PO đang mở (CONFIRMED hoặc PARTIALLY_RECEIVED).
     */
    BigDecimal getIncomingStockQty(UUID variantId);

    /**
     * Lấy số lượng hàng đang đặt (sắp về) cho danh sách SKU từ các PO đang mở.
     */
    Map<UUID, BigDecimal> getIncomingStockQtyMap(Collection<UUID> variantIds);

    /**
     * Lấy snapshot đơn mua hàng phục vụ nhập kho (UC-32): trạng thái, kho nhận và các dòng
     * hàng kèm số còn phải nhận. Trả về {@link Optional#empty()} nếu không tồn tại PO.
     *
     * <p>Module Inventory dùng view này để kiểm tra điều kiện nhận và duyệt từng dòng trước khi
     * lập phiếu, thay vì truy cập entity internal của Purchasing.</p>
     */
    Optional<ReceivablePurchaseOrderView> getReceivablePurchaseOrder(UUID purchaseOrderId);

    /**
     * Nhận thông báo xác nhận phiếu nhập hàng từ Module Inventory để cập nhật tiến độ nhận và trạng thái PO.
     *
     * <p>Đây là chốt chặn cuối cùng mang tính giao dịch của Purchasing: phương thức cộng dồn
     * {@code quantityReceived}, kiểm tra không cho nhận vượt số đã đặt và tự chuyển PO sang
     * {@code PARTIALLY_RECEIVED} hoặc {@code COMPLETED}. Chạy chung transaction với bên gọi nên
     * nếu vi phạm sẽ ném lỗi và cuộn ngược toàn bộ thao tác nhập kho.</p>
     */
    void onGoodsReceiptConfirmed(UUID purchaseOrderId, Map<UUID, BigDecimal> receivedItems);
}