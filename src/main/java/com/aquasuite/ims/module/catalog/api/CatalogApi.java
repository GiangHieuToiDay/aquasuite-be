package com.aquasuite.ims.module.catalog.api;

import com.aquasuite.ims.module.catalog.api.dto.CatalogBundleSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Interface của Module Catalog.
 * Các module khác (Sales, Inventory, ServiceDesk, Purchasing, AiAdvisor, ...) giao tiếp đồng bộ với Catalog qua Interface này.
 */
public interface CatalogApi {

    /** Tra cứu thông tin SKU theo variantId */
    Optional<CatalogSkuSummaryDto> getSkuSummary(UUID variantId);

    /** Tra cứu thông tin SKU theo mã sku */
    Optional<CatalogSkuSummaryDto> getSkuSummaryBySku(String sku);

    /** Tra cứu danh sách SKU theo tập hợp variantIds */
    Map<UUID, CatalogSkuSummaryDto> getSkuSummaries(Collection<UUID> variantIds);

    /** Tra cứu thông tin combo/bundle theo bundleId */
    Optional<CatalogBundleSummaryDto> getBundleSummary(UUID bundleId);

    /** Tra cứu danh sách SKU active thuộc tập hợp productIds */
    List<CatalogSkuSummaryDto> getSkusByProductIds(Collection<UUID> productIds);

    /** Lấy ID của mọi SKU thuộc một sản phẩm gốc, kể cả SKU đã xóa mềm. */
    List<UUID> getVariantIdsByProductId(UUID productId);

    /** Lấy toàn bộ SKU đang ở trạng thái ACTIVE */
    List<CatalogSkuSummaryDto> getAllActiveSkus();
}
