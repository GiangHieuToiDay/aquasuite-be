package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;

import java.util.UUID;

public interface BrandService {

    BrandPageResponse listBrands(int page, int size, String search, String status, String sortBy, String direction);

    BrandResponse getBrandDetail(UUID brandId);

    BrandResponse createBrand(CreateBrandRequest request);

    BrandResponse updateBrand(UUID brandId, UpdateBrandRequest request);

    void deleteBrand(UUID brandId);
}
