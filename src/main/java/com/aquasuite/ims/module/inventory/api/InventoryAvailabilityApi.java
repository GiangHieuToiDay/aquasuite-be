package com.aquasuite.ims.module.inventory.api;
import java.math.BigDecimal;
import java.util.*;
/**
 * Hợp đồng đọc tồn cho preview combo tại kho duy nhất hiện có; không giữ hoặc ghi tồn.
 * Giá trị khả dụng = tồn thực tế - tồn đã giữ. SKU chưa có bản ghi được bỏ khỏi kết quả,
 * để Catalog trả UNKNOWN thay vì tự coi là hết hàng.
 * Triển khai dùng transaction đọc REQUIRES_NEW để lỗi đọc tồn không làm hỏng transaction Catalog.
 * Lỗi được chuyển về bên gọi để preview trả UNKNOWN. API nội bộ không cần tham số actor;
 * việc xác thực JWT của HTTP request vẫn do bộ lọc dùng chung xử lý.
 */
public interface InventoryAvailabilityApi {
    Map<UUID, BigDecimal> availableQuantities(Collection<UUID> variantIds);
}
