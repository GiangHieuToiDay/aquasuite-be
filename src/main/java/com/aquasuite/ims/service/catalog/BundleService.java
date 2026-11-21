package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;

import java.util.UUID;

/**
 * Service quản lý combo/bundle và tra cứu gói sản phẩm hợp nhất.
 */
public interface BundleService {

    // ── Bundle Admin Operations ──────────────────────────────────────────────

    BundleResponse createBundle(CreateBundleRequest request);

    BundleResponse updateBundle(UUID id, UpdateBundleRequest request);

    BundleResponse changeProductStatus(UUID id, ChangeBundleStatusRequest request);

    BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request);

    void deleteBundle(UUID id, long expectedVersion);

    // ── Bundle Query & Storefront Operations ─────────────────────────────────

    PageResponse<BundleResponse> getBundleList(BundlePageRequest request);

    PageResponse<BundleResponse> searchBundles(BundlePageRequest request);

    BundleResponse getBundleDetails(UUID id);

    PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest request);

    BundleResponse getPublishedBundleDetails(UUID id);

    PageResponse<BundleSkuResponse> searchSkus(int page, int size, String keyword);
}
