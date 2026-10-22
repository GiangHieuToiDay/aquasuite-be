package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.ProductSearchCriteria;
import com.aquasuite.ims.module.catalog.internal.payload.res.ProductFacetResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.StorefrontProductDetailResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.StorefrontSearchResultResponse;
import com.aquasuite.ims.module.catalog.internal.service.ProductStorefrontService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Controller phục vụ cho Khách hàng (Storefront) và Nhân viên bán hàng (POS).
 * Use Case: UC-12 Search and View Product Information (FE-03).
 * Không yêu cầu quyền Admin; mở cho Khách vãng lai, Khách hàng và Nhân viên bán hàng.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/catalog/storefront")
@RequiredArgsConstructor
public class ProductStorefrontController {

    private final ProductStorefrontService productStorefrontService;

    /**
     * Tìm kiếm và lọc sản phẩm với bộ lọc đa tiêu chí (Faceted Search):
     * - Từ khóa tìm kiếm toàn văn (keyword: tên, mã, mô tả, biến thể SKU)
     * - Lọc theo Danh mục (categoryId, categoryCode)
     * - Lọc theo Thương hiệu (brandId, brandCode)
     * - Thanh trượt khoảng giá (minPrice, maxPrice)
     * - Nút gạt trạng thái còn hàng ("In Stock Only")
     * - Bộ lọc màu sắc, kích thước / tâm xả (color, size)
     * - Sắp xếp & phân trang
     * Khi không có kết quả: Trả về thông báo MSG43 kèm danh mục gợi ý phổ biến.
     */
    @GetMapping({"/search", "/products"})
    public ResponseEntity<ApiResponse<StorefrontSearchResultResponse>> searchProducts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(required = false) String categoryCode,
            @RequestParam(required = false) UUID brandId,
            @RequestParam(required = false) String brandCode,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) Boolean inStockOnly,
            @RequestParam(required = false) String color,
            @RequestParam(name = "dimension", required = false) String dimension,
            @RequestParam(required = false) String sortBy,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        ProductSearchCriteria criteria = ProductSearchCriteria.builder()
                .keyword(keyword)
                .categoryId(categoryId)
                .categoryCode(categoryCode)
                .brandId(brandId)
                .brandCode(brandCode)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .inStockOnly(inStockOnly)
                .color(color)
                .size(dimension)
                .sortBy(sortBy)
                .build();

        StorefrontSearchResultResponse response = productStorefrontService.searchProducts(criteria, pageable);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    /**
     * Lấy dữ liệu cho thanh bộ lọc đa diện bên hông (Faceted Filter Sidebar):
     * - Danh mục và số lượng sản phẩm
     * - Thương hiệu và số lượng sản phẩm
     * - Khoảng giá tối thiểu và tối đa
     * - Danh sách màu sắc, kích thước
     * - Thông số kỹ thuật mẫu (Flushing type, Rough-in dimension, Glaze tech)
     */
    @GetMapping("/facets")
    public ResponseEntity<ApiResponse<ProductFacetResponse>> getFacets() {
        ProductFacetResponse facets = productStorefrontService.getFacets();
        return ResponseEntity.ok(ApiResponse.ok(facets));
    }

    /**
     * Xem trang chi tiết sản phẩm toàn diện (Product Details Page):
     * - Bộ sưu tập hình ảnh kèm khả năng phóng to (Zoom)
     * - Bản vẽ kỹ thuật CAD Blueprints
     * - Bộ chọn biến thể (Variant selector: Color, Size/Rough-in)
     * - Tồn kho thời gian thực:
     *   + Khách hàng: Trạng thái In Stock / Out of Stock
     *   + Nhân viên bán hàng (Sales Staff): Chi tiết số lượng theo từng kho
     * - Bảng thông số kỹ thuật và tài liệu hướng dẫn lắp đặt PDF
     * - Điều khoản và chính sách bảo hành
     */
    @GetMapping("/products/{productId}")
    public ResponseEntity<ApiResponse<StorefrontProductDetailResponse>> getProductDetail(
            @PathVariable UUID productId
    ) {
        StorefrontProductDetailResponse response = productStorefrontService.getProductDetail(productId);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
