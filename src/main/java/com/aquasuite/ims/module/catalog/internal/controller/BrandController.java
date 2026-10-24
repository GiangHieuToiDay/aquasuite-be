package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBrandRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandPageResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BrandResponse;
import com.aquasuite.ims.module.catalog.internal.service.BrandService;
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
@RequestMapping("/api/v1/brands")
@RequiredArgsConstructor
public class BrandController {

    private final BrandService brandService;

    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<BrandPageResponse> listBrands(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "code") String sortBy,
            @RequestParam(defaultValue = "asc") String direction
    ) {
        return ApiResponse.ok(brandService.listBrands(page, size, search, status, sortBy, direction));
    }

    @GetMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<BrandResponse> getBrandDetail(@PathVariable UUID brandId) {
        return ApiResponse.ok(brandService.getBrandDetail(brandId));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request
    ) {
        BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    @PutMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_UPDATE')")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable UUID brandId,
            @Valid @RequestBody UpdateBrandRequest request
    ) {
        return ApiResponse.ok(brandService.updateBrand(brandId, request));
    }

    @DeleteMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_DELETE')")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID brandId) {
        brandService.deleteBrand(brandId);
        return ApiResponse.ok(null);
    }
}
