package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;
import com.aquasuite.ims.common.PageResponse;

import java.util.UUID;

public interface BundleQueryService {
    PageResponse<BundleResponse> getBundleList(BundlePageRequest request);

    PageResponse<BundleResponse> searchBundles(BundlePageRequest request);

    BundleResponse getBundleDetails(UUID id);

    PageResponse<BundleResponse> getPublishedBundles(BundlePageRequest request);

    BundleResponse getPublishedBundleDetails(UUID id);

    PageResponse<BundleSkuResponse> searchSkus(int page, int size, String keyword);
}
