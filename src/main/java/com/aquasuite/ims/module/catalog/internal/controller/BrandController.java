package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandBriefResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
import com.aquasuite.ims.module.catalog.internal.service.BrandService;
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
@RequestMapping("/api/v1/catalog/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    /** Trả về danh sách thương hiệu hoạt động dùng cho bộ lọc và dropdown. */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<List<BrandBriefResponse>> getActiveBrandOptions() {
        return ApiResponse.ok(brandService.getActiveBrandOptions());
    }

    /** Tìm kiếm và phân trang thương hiệu theo trạng thái. */
    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<PageResponse<BrandResponse>> listBrands(
            @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(brandService.listBrands(pageQuery, search, status));
    }

    /** Trả về chi tiết một thương hiệu theo ID. */
    @GetMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<BrandResponse> getBrandDetail(@PathVariable UUID brandId) {
        return ApiResponse.ok(brandService.getBrandDetail(brandId));
    }

    /** Tạo thương hiệu mới khi người dùng có quyền tương ứng. */
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request
    ) {
        BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** Thay đổi thông tin thương hiệu hiện có. */
    @PutMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_UPDATE')")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable UUID brandId,
            @Valid @RequestBody UpdateBrandRequest request
    ) {
        return ApiResponse.ok(brandService.updateBrand(brandId, request));
    }

    /** Xóa mềm thương hiệu nếu không còn sản phẩm tham chiếu. */
    @DeleteMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_DELETE')")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID brandId) {
        brandService.deleteBrand(brandId);
        return ApiResponse.ok(null);
    }
}
