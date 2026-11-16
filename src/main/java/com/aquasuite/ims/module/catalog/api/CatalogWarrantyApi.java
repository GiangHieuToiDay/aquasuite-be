package com.aquasuite.ims.module.catalog.api;

import com.aquasuite.ims.module.catalog.api.dto.CatalogWarrantyPolicyDto;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Hợp đồng công khai tra cứu thông tin chính sách bảo hành từ module Catalog (UC-45).
 * Tách riêng khỏi {@link CatalogApi} để không làm thay đổi chữ ký hay dependency của các module hiện hữu.
 */
public interface CatalogWarrantyApi {

    /**
     * Tra cứu SKU và thông tin chính sách bảo hành gắn với variantId (nếu có).
     * Trả {@link Optional#empty()} nếu bản thân SKU không tồn tại; nếu SKU tồn tại nhưng chưa gắn chính sách
     * thì trả DTO với {@code warrantyPolicyId == null} và {@code warrantyMonths == 0}.
     */
    Optional<CatalogWarrantyPolicyDto> getWarrantyInfoByVariantId(UUID variantId);

    /** Tra cứu chi tiết một chính sách bảo hành theo warrantyPolicyId. */
    Optional<CatalogWarrantyPolicyDto> getWarrantyPolicyById(UUID policyId);

    /** Lấy danh sách chính sách bảo hành đang ở trạng thái ACTIVE. */
    List<CatalogWarrantyPolicyDto> getActiveWarrantyPolicies();

    /** Tra cứu chính sách ACTIVE đang áp dụng theo cặp Brand + Category của SPU. */
    Optional<CatalogWarrantyPolicyDto> getActiveWarrantyPolicyByBrandAndCategory(UUID brandId, UUID categoryId);
}

