package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.*;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;

import java.util.UUID;

public interface BundleAdminService {
    BundleResponse createBundle(CreateBundleRequest request);

    BundleResponse updateBundle(UUID id, UpdateBundleRequest request);

    BundleResponse changeProductStatus(UUID id, ChangeBundleStatusRequest request);

    BundleResponse changePublicationStatus(UUID id, ChangePublicationStatusRequest request);

    void deleteBundle(UUID id, long expectedVersion);
}
