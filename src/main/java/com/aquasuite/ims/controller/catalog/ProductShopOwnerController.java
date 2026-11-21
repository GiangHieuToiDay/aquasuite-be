package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.enums.catalog.ProductType;
import com.aquasuite.ims.dto.catalog.request.CreateProductRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateProductRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateProductStatusRequest;
import com.aquasuite.ims.dto.catalog.response.ProductDetailResponse;
import com.aquasuite.ims.dto.catalog.response.ProductSummaryResponse;
import com.aquasuite.ims.service.catalog.ProductShopOwnerService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller quản lý sản phẩm dành riêng cho Chủ cửa hàng (Shop Owner).
 * Quyền yêu cầu toàn bộ endpoint: SHOP_OWNER
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/catalog")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHOP_OWNER')")
public class ProductShopOwnerController {

    private final ProductShopOwnerService productShopOwnerService;

    /**
     * Tạo mới sản phẩm (kèm biến thể SKU và hình ảnh)
     * Quyền yêu cầu: SHOP_OWNER
     */
    @PostMapping("/products")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> createProduct(
            @Valid @RequestBody CreateProductRequest request
    ) {
        ProductDetailResponse response = productShopOwnerService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Product created successfully. Please configure SKUs and variants."));
    }

    /**
     * Lấy danh sách sản phẩm có phân trang, tìm kiếm theo tên/mã, và lọc theo danh mục/thương hiệu/trạng thái
     * Quyền yêu cầu: SHOP_OWNER
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
        Page<ProductSummaryResponse> response = productShopOwnerService.listProducts(
                keyword, categoryId, brandId, status, productType, pageable
        );
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Xem thông tin chi tiết một sản phẩm theo ID (kèm toàn bộ biến thể và hình ảnh)
     * Quyền yêu cầu: SHOP_OWNER
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> getProductDetail(
            @PathVariable UUID productId
    ) {
        ProductDetailResponse response = productShopOwnerService.getProductDetail(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Cập nhật thông tin sản phẩm và danh sách biến thể
     * Quyền yêu cầu: SHOP_OWNER
     */
    @PutMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductRequest request
    ) {
        ProductDetailResponse response = productShopOwnerService.updateProduct(productId, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Product details updated successfully."));
    }

    /**
     * Đổi trạng thái sản phẩm (Bản nháp / Hoạt động / Ngừng kinh doanh)
     * Quyền yêu cầu: SHOP_OWNER
     */
    @PatchMapping("/products/{productId}/status")
    public ResponseEntity<ApiResponse<ProductDetailResponse>> updateProductStatus(
            @PathVariable UUID productId,
            @Valid @RequestBody UpdateProductStatusRequest request
    ) {
        ProductDetailResponse response = productShopOwnerService.updateProductStatus(productId, request);
        String message = request.status() == ProductStatus.DISCONTINUED
                ? "Product marked as Discontinued."
                : "Cập nhật trạng thái sản phẩm thành công";
        return ResponseEntity.ok(ApiResponse.ok(response, message));
    }

    /**
     * Xóa mềm sản phẩm (chuyển sang trạng thái DELETED)
     * Quyền yêu cầu: SHOP_OWNER
     */
    @DeleteMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(
            @PathVariable UUID productId
    ) {
        productShopOwnerService.deleteProduct(productId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Xóa sản phẩm thành công"));
    }

}
