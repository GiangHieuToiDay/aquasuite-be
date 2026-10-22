package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantDeleteRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantMatrixRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.VariantUpsertRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantDeletionEligibilityResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.VariantStatsResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface VariantService {
    Page<VariantResponse> list(UUID productId, String keyword, VariantStatus status, Pageable pageable);

    VariantStatsResponse stats(UUID productId);

    VariantResponse create(UUID productId, VariantUpsertRequest request);

    List<VariantResponse> createMatrix(UUID productId, VariantMatrixRequest request);

    VariantResponse update(UUID productId, UUID variantId, VariantUpsertRequest request);

    VariantResponse changeStatus(UUID productId, UUID variantId, VariantStatusRequest request);

    VariantDeletionEligibilityResponse deletionEligibility(UUID productId, UUID variantId);

    void delete(UUID productId, UUID variantId, VariantDeleteRequest request);
}
