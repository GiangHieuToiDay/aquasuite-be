package com.aquasuite.ims.module.inventory.internal.listener;

import com.aquasuite.ims.module.inventory.api.event.LowStockAlertEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Tiêu thụ sự kiện cảnh báo tồn thấp của UC-31.
 * Khi một SKU tụt xuống mức tồn thấp (do đặt giữ hàng, điều chỉnh hoặc cấu hình lại ngưỡng),
 * listener này ghi nhận cảnh báo để phục vụ giám sát. Theo GB-27, hệ thống chỉ cảnh báo cho nhân viên
 * xem xét, tuyệt đối không tự động chọn số lượng mua hay gửi đơn mua cho nhà cung cấp.
 * Chạy sau khi giao dịch gốc commit để chỉ cảnh báo trên thay đổi tồn đã được lưu thật.
 */
@Slf4j
@Component
public class LowStockAlertListener {

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLowStockAlert(LowStockAlertEvent event) {
        if (event == null) {
            return;
        }
        log.warn("[Inventory] Cảnh báo tồn thấp - variantId={}, onHand={}, available={}, minimum={}",
                event.variantId(), event.onHandQty(), event.availableQty(), event.minimumQty());
        // Điểm mở rộng: đẩy thông báo cho Chủ cửa hàng / badge trên dashboard (UC-53) khi cần.
    }
}
