package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateCategoryRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryResponse;
import com.aquasuite.ims.module.catalog.internal.service.CategoryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** Trả về danh sách danh mục hoạt động dùng cho bộ lọc và dropdown. */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<List<CategoryBriefResponse>> getActiveCategoryOptions() {
        return ApiResponse.ok(categoryService.getActiveCategoryOptions());
    }

    /** Tìm kiếm và phân trang danh mục theo trạng thái. */
    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<PageResponse<CategoryResponse>> listCategories(
            @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(categoryService.listCategories(pageQuery, search, status));
    }

    /** Trả về chi tiết một danh mục theo ID. */
    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<CategoryResponse> getCategoryDetail(@PathVariable UUID categoryId) {
        return ApiResponse.ok(categoryService.getCategoryDetail(categoryId));
    }

    /** Tạo danh mục mới và kiểm tra quan hệ cha nếu được cung cấp. */
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** Cập nhật danh mục và ngăn tạo chu trình phân cấp. */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_UPDATE')")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ApiResponse.ok(categoryService.updateCategory(categoryId, request));
    }

    /** Xóa mềm danh mục nếu không còn danh mục con hoặc sản phẩm tham chiếu. */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_DELETE')")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.ok(null);
    }
}
