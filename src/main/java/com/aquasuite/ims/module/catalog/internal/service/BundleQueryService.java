package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleSkuResponse;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface BundleQueryService {
    PageResponse<BundleResponse> getBundleList(BundlePageRequest request);

    PageResponse<BundleResponse> searchBundles(BundlePageRequest request);

    BundleResponse getBundleDetails(UUID id);

    PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest request);

    BundleResponse getPublishedBundleDetails(UUID id);

    PageResponse<BundleSkuResponse> searchSkus(int page, int size, String keyword);
}
