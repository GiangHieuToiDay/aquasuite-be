package com.aquasuite.ims.service.catalog;

import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;

import java.util.UUID;

public interface BundleAdminService {
    BundleResponse createBundle(CreateBundleRequest request);

    BundleResponse updateBundle(UUID id, UpdateBundleRequest request);

    BundleResponse changeProductStatus(UUID id, ChangeBundleStatusRequest request);

    BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request);

    void deleteBundle(UUID id, long expectedVersion);
}
