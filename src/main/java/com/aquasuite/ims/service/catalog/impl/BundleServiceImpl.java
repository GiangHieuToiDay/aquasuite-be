package com.aquasuite.ims.service.catalog.impl;

import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;
import com.aquasuite.ims.service.catalog.BundleService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Primary
@RequiredArgsConstructor
public class BundleServiceImpl implements BundleService {

    private final BundleAdminServiceImpl adminService;
    private final BundleQueryServiceImpl queryService;

    // ── Bundle Admin Operations ──────────────────────────────────────────────

    @Override
    public BundleResponse createBundle(CreateBundleRequest request) {
        return adminService.createBundle(request);
    }

    @Override
    public BundleResponse updateBundle(UUID id, UpdateBundleRequest request) {
        return adminService.updateBundle(id, request);
    }

    @Override
    public BundleResponse changeProductStatus(UUID id, ChangeBundleStatusRequest request) {
        return adminService.changeProductStatus(id, request);
    }

    @Override
    public BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request) {
        return adminService.changePublicationStatus(id, request);
    }

    @Override
    public void deleteBundle(UUID id, long expectedVersion) {
        adminService.deleteBundle(id, expectedVersion);
    }

    // ── Bundle Query & Storefront Operations ─────────────────────────────────

    @Override
    public PageResponse<BundleResponse> getBundleList(BundlePageRequest request) {
        return queryService.getBundleList(request);
    }

    @Override
    public PageResponse<BundleResponse> searchBundles(BundlePageRequest request) {
        return queryService.searchBundles(request);
    }

    @Override
    public BundleResponse getBundleDetails(UUID id) {
        return queryService.getBundleDetails(id);
    }

    @Override
    public PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest request) {
        return queryService.getPublishedBundles(request);
    }

    @Override
    public BundleResponse getPublishedBundleDetails(UUID id) {
        return queryService.getPublishedBundleDetails(id);
    }

    @Override
    public PageResponse<BundleSkuResponse> searchSkus(int page, int size, String keyword) {
        return queryService.searchSkus(page, size, keyword);
    }
}
