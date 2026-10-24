package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface BrandService {

    PageResponse<BrandResponse> listBrands(PageQuery pageQuery, String search, String status);

    BrandResponse getBrandDetail(UUID brandId);

    BrandResponse createBrand(CreateBrandRequest request);

    BrandResponse updateBrand(UUID brandId, UpdateBrandRequest request);

    void deleteBrand(UUID brandId);
}
