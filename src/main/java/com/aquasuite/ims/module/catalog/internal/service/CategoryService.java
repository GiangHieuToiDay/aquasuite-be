package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;

import java.util.UUID;

public interface CategoryService {

    CategoryPageResponse listCategories(int page, int size, String search, String status, String sortBy, String direction);

    CategoryResponse getCategoryDetail(UUID categoryId);

    CategoryResponse createCategory(CreateCategoryRequest request);

    CategoryResponse updateCategory(UUID categoryId, UpdateCategoryRequest request);

    void deleteCategory(UUID categoryId);
}
