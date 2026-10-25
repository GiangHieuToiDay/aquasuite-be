package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface CategoryService {

    PageResponse<CategoryResponse> listCategories(PageQuery pageQuery, String search, String status);

    CategoryResponse getCategoryDetail(UUID categoryId);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request);

    void deleteCategory(UUID categoryId);
}
