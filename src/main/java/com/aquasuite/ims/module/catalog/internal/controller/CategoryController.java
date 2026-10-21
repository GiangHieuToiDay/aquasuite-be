package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.request.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.request.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.response.CategoryPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.response.CategoryResponse;
import com.aquasuite.ims.module.catalog.internal.service.CategoryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<CategoryPageResponse> listCategories(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return ApiResponse.ok(categoryService.listCategories(page, size, search, status, sortBy, direction));
    }

    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<CategoryResponse> getCategoryDetail(@PathVariable UUID categoryId) {
        return ApiResponse.ok(categoryService.getCategoryDetail(categoryId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_UPDATE')")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ApiResponse.ok(categoryService.updateCategory(categoryId, request));
    }

    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_DELETE')")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.ok(null);
    }
}
