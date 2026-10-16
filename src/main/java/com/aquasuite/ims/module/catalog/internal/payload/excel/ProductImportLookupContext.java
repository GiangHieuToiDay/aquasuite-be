package com.aquasuite.ims.module.catalog.internal.payload.excel;

import com.aquasuite.ims.module.catalog.events.VariantsBulkImportedEvent.InitialStockItem;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.*;

/**
 * Bộ nhớ đệm tra cứu trên RAM trong suốt quá trình import để chống N+1 Query.
 */
@Getter
@RequiredArgsConstructor
public class ProductImportLookupContext {

    // 1. Chế độ Import người dùng đã chọn (SKIP_EXISTING hoặc OVERWRITE_EXISTING)
    private final ImportMode importMode;

    // 2. Từ điển tra cứu nhanh Category & Brand đang ACTIVE trong DB
    private final Map<String, UUID> categoryCodeToIdMap;
    private final Map<String, UUID> brandCodeToIdMap;

    // 3. Tập hợp phát hiện trùng SKU ngay bên trong cùng 1 file Excel
    private final Set<String> seenSkusInFile = new HashSet<>();

    // 3b. Chữ ký (tên SPU | category | brand) của từng product_code đã gặp trong file
    private final Map<String, String> productSignatureInFile = new HashMap<>();

    // 4. Danh sách gom các SKU tạo mới để bắn Event sang module inventory
    private final List<InitialStockItem> initialStockItemsToPublish = new ArrayList<>();
}
