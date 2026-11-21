package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.response.SalesOrderItemWarrantySnapshotDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderWarrantySummaryDto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng công khai bổ sung từ module Sales phục vụ UC-45 (Quản lý thẻ bảo hành).
 * Tách riêng khỏi {@link SalesOrderService} để giữ nguyên chữ ký và test hiện hữu.
 */
public interface SalesWarrantyService {

    /**
     * Khóa bi quan dòng {@code order_items} trong transaction đang mở của bên gọi và trả snapshot đơn/dòng hàng.
     */
    Optional<SalesOrderItemWarrantySnapshotDto> lockOrderItemForWarranty(UUID orderItemId);

    /**
     * Đọc thông tin tổng hợp đơn hàng và hóa đơn mới nhất (ưu tiên hóa đơn STORE) để hiển thị hồ sơ bảo hành.
     */
    Optional<SalesOrderWarrantySummaryDto> getOrderSummaryWithInvoice(UUID orderId);

    /**
     * Tra cứu hàng loạt mã đơn hàng theo tập {@code orderIds} để tránh N+1 khi phân trang danh sách bảo hành.
     */
    Map<UUID, String> getOrderCodesByIds(Collection<UUID> orderIds);
}

