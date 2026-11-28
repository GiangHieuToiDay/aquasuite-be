package com.aquasuite.ims.service.purchasing;

import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderBriefView;
import com.aquasuite.ims.dto.purchasing.response.VariantPurchaseOrderLineView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Contract truy vấn chỉ đọc từ Purchasing phục vụ màn hình Quản lý Tồn kho theo dõi tiến độ PO.
 * Tách độc lập với {@link PurchaseOrderService} để tránh phụ thuộc vòng giữa Inventory và Purchasing.
 */
public interface PurchaseOrderTrackingQueryService {

    /**
     * Lấy các dòng đơn mua hàng của tập biến thể.
     *
     * @param variantIds       danh sách biến thể cần tra cứu
     * @param includeCompleted true nếu lấy cả đơn đã hoàn thành gần đây; false nếu chỉ lấy đơn đang mở (CONFIRMED, PARTIALLY_RECEIVED)
     */
    List<VariantPurchaseOrderLineView> findLinesByVariantIds(Collection<UUID> variantIds, boolean includeCompleted);

    /**
     * Lấy toàn bộ dòng đơn mua hàng đang mở (CONFIRMED, PARTIALLY_RECEIVED) phục vụ tính KPI tổng quan kho.
     */
    List<VariantPurchaseOrderLineView> findAllOpenLines();

    /**
     * Tra cứu thông tin tóm tắt đơn mua hàng theo danh sách ID.
     */
    Map<UUID, PurchaseOrderBriefView> findOrderBriefsByIds(Collection<UUID> purchaseOrderIds);
}
