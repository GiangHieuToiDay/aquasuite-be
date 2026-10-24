package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductType;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateProductStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.CategoryBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductDetailResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductSummaryResponse;
import com.aquasuite.ims.module.catalog.internal.service.ProductAdminService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
public class ProductAdminController {

    private final ProductAdminService productAdminService;
    private final IamApi iamApi;

    /**
     * Tạo mới sản phẩm (kèm biến thể SKU và hình ảnh)
     * Quyền yêu cầu: ADMIN
     */
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        iamApi.validateAdminAccess();
        ProductDetailResponse response = productAdminService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Product created successfully. Please configure SKUs and variants."));
    }

    /**
     * Lấy danh sách sản phẩm có phân trang, tìm kiếm theo tên/mã, và lọc theo danh mục/thương hiệu/trạng thái
     * Quyền yêu cầu: ADMIN
     */
    @GetMapping("/products")
    public ResponseEntity<ApiResponse<Page<ProductSummaryResponse>>> listProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(required = false) ProductType productType,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        iamApi.validateAdminAccess();
        Page<ProductSummaryResponse> response = productAdminService.listProducts(
                keyword, categoryId, brandId, status, productType, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Xem thông tin chi tiết một sản phẩm theo ID (kèm toàn bộ biến thể và hình ảnh)
     * Quyền yêu cầu: ADMIN
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(
            @PathVariable UUID productId
    ) {
        iamApi.validateAdminAccess();
        ProductDetailResponse response = productAdminService.getProductDetail(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Cập nhật thông tin sản phẩm và danh sách biến thể
     * Quyền yêu cầu: ADMIN
     */
    @PutMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        iamApi.validateAdminAccess();
        ProductDetailResponse response = productAdminService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Product details updated successfully."));
    }

    /**
     * Đổi trạng thái sản phẩm (Bản nháp / Hoạt động / Ngừng kinh doanh)
     * Quyền yêu cầu: ADMIN
     */
    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductStatus(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusRequest request
    ) {
        iamApi.validateAdminAccess();
        ProductDetailResponse response = productAdminService.updateProductStatus(productId, request);
        String message = request.status() == ProductStatus.DISCONTINUED
                ? "Product marked as Discontinued."
                : "Cập nhật trạng thái sản phẩm thành công";
        return ResponseEntity.ok(ApiResponse.ok(response, message));
    }

    /**
     * Xóa mềm sản phẩm (chuyển sang trạng thái DELETED)
     * Quyền yêu cầu: ADMIN
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID productId
    ) {
        iamApi.validateAdminAccess();
        productAdminService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa sản phẩm thành công"));
    }

    /**
     * Lấy danh sách danh mục hoạt động (phục vụ bộ lọc và form chọn danh mục)
     */
    @GetMapping("/categories")
    public ResponseEntity<ApiResponse<List<CategoryBriefResponse>>> getActiveCategories() {
        List<CategoryBriefResponse> categories = productAdminService.getActiveCategories();
        return ResponseEntity.ok(ApiResponse.ok(categories));
    }

    /**
     * Lấy danh sách thương hiệu hoạt động (phục vụ bộ lọc và form chọn thương hiệu)
     */
    @GetMapping("/brands")
    public ResponseEntity<ApiResponse<List<BrandBriefResponse>>> getActiveBrands() {
        List<BrandBriefResponse> brands = productAdminService.getActiveBrands();
        return ResponseEntity.ok(ApiResponse.ok(brands));
    }
}
