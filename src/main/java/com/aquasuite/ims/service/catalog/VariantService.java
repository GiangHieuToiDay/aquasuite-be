package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.enums.catalog.VariantStatus;
import com.aquasuite.ims.dto.catalog.request.VariantDeleteRequest;
import com.aquasuite.ims.dto.catalog.request.VariantMatrixRequest;
import com.aquasuite.ims.dto.catalog.request.VariantStatusRequest;
import com.aquasuite.ims.dto.catalog.request.VariantUpsertRequest;
import com.aquasuite.ims.dto.catalog.response.VariantDeletionEligibilityResponse;
import com.aquasuite.ims.dto.catalog.response.VariantResponse;
import com.aquasuite.ims.dto.catalog.response.VariantStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface VariantService {
    Page<VariantResponse> list(UUID productId, String keyword, VariantStatus status, boolean missingPrice,
                               String sortBy,
                               String sortDir,
                               String warehouseCode, Pageable pageable);

    VariantStatsResponse stats(UUID productId);

    VariantResponse create(UUID productId, VariantUpsertRequest request);

    List<VariantResponse> createMatrix(UUID productId, VariantMatrixRequest request);

    VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request);

    VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request);

    VariantDeletionEligibilityResponse deletionEligibility(UUID productId, UUID variantId);

    void delete(UUID productId, UUID variantId, VariantDeleteRequest request);
}
