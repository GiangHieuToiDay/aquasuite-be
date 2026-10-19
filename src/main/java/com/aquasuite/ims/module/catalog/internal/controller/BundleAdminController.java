package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleAdminService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/v1/catalog/admin/bundles")
@RequiredArgsConstructor
public class BundleAdminController {
    private final BundleAdminService service;

    private void query(HttpServletRequest r, Set<String> allowed) {
        if (!allowed.containsAll(r.getParameterMap().keySet())
                || r.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }

    @GetMapping
    public ApiResponse<BundlePage<BundleCoreSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus productStatus,
            @RequestParam(required = false) PublicationStatus publicationStatus,
            HttpServletRequest request) {
        query(request, Set.of("page", "size", "keyword", "productStatus", "publicationStatus"));
        return ApiResponse.ok(service.list(page, size, keyword, productStatus, publicationStatus));
    }

    @GetMapping("/{bundleId}")
    public ApiResponse<BundleCoreDetail> get(
            @PathVariable UUID bundleId, HttpServletRequest request) {
        query(request, Set.of());
        return ApiResponse.ok(service.get(bundleId));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<BundleCoreDetail>> create(
            @Valid @RequestBody BundleCoreCreateRequest r, HttpServletRequest request) {
        query(request, Set.of());
        var d = service.create(r);
        return ResponseEntity.created(URI.create("/api/v1/catalog/admin/bundles/" + d.bundleId()))
                .body(ApiResponse.ok(d, "Đã tạo combo."));
    }

    @PutMapping("/{bundleId}")
    public ApiResponse<BundleCoreDetail> update(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleCoreUpdateRequest r,
            HttpServletRequest request) {
        query(request, Set.of());
        return ApiResponse.ok(service.update(bundleId, r));
    }

    @DeleteMapping("/{bundleId}")
    public ApiResponse<BundleCoreMutationResult> delete(
            @PathVariable UUID bundleId,
            @RequestParam long expectedVersion,
            HttpServletRequest request) {
        query(request, Set.of("expectedVersion"));
        return ApiResponse.ok(service.delete(bundleId, expectedVersion), "Đã xóa combo.");
    }

    @PatchMapping("/{bundleId}/status")
    public ApiResponse<BundleCoreMutationResult> status(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleCoreStatusRequest r,
            HttpServletRequest request) {
        query(request, Set.of());
        return ApiResponse.ok(service.status(bundleId, r));
    }
}
