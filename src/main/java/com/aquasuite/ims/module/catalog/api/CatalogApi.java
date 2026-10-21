package com.aquasuite.ims.module.catalog.api;

import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Public Interface của Module Catalog.
 * Các module khác (Sales, Inventory, ServiceDesk, AiAdvisor, ...) giao tiếp đồng bộ với Catalog qua Interface này.
 */
public interface CatalogApi {

    /** Tra cứu thông tin SKU theo variantId */
    Optional<CatalogSkuSummaryDto> getSkuSummary(UUID variantId);

    /** Tra cứu thông tin SKU theo mã sku */
    Optional<CatalogSkuSummaryDto> getSkuSummaryBySku(String sku);

    /** Tra cứu danh sách SKU theo tập hợp variantIds */
    Map<UUID, CatalogSkuSummaryDto> getSkuSummaries(Collection<UUID> variantIds);
}
