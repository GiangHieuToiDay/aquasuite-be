package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.entity.catalog.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.enums.catalog.ProductStatus;
import com.aquasuite.ims.dto.catalog.request.*;
import com.aquasuite.ims.dto.catalog.response.BundleEvaluationResponse;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.dto.catalog.response.BundleSkuResponse;
import com.aquasuite.ims.service.catalog.BundleService;
import com.aquasuite.ims.service.catalog.impl.BundleServiceImpl;
import com.aquasuite.ims.service.catalog.helper.BundleEvaluationService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Set;
import java.util.UUID;

/**
 * Controller Quản trị và Tra cứu Combo/Bundle.
 */
@RestController
@RequestMapping("/api/v1/catalog/bundle")
public class BundleController {

    private final BundleService bundleService;
    private final BundleEvaluationService evaluation;

    @Autowired
    public BundleController(BundleService bundleService, BundleEvaluationService evaluation) {
        this.bundleService = bundleService;
        this.evaluation = evaluation;
    }

    // ── Public Storefront Endpoints ──────────────────────────────────────────

    @GetMapping
    public ApiResponse<PageResponse<BundleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        validateQuery(request, Set.of("page", "size", "keyword"));
        return ApiResponse
                .ok(bundleService.getPublishedBundles(new BundlePageRequest(page, size, keyword, null, null)));
    }

    @GetMapping("/{bundleId}")
    public ApiResponse<BundleResponse> get(
            @PathVariable UUID bundleId, HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(bundleService.getPublishedBundleDetails(bundleId));
    }

    @PostMapping("/{bundleId}/evaluations")
    public ApiResponse<BundleEvaluationResponse> evaluate(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleEvaluationRequest payload,
            HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(evaluation.checkAvailability(bundleId, payload));
    }

    // ── Admin Endpoints (from BundleAdminController at /admin) ────────────────

    @GetMapping("/admin")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<PageResponse<BundleResponse>> listAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus productStatus,
            @RequestParam(required = false) PublicationStatus publicationStatus,
            HttpServletRequest request) {
        validateQuery(request, Set.of("page", "size", "keyword", "productStatus", "publicationStatus"));
        return ApiResponse.ok(bundleService
                .searchBundles(new BundlePageRequest(page, size, keyword, productStatus, publicationStatus)));
    }

    @GetMapping("/admin/{bundleId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<BundleResponse> getAdmin(@PathVariable UUID bundleId, HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(bundleService.getBundleDetails(bundleId));
    }

    @PostMapping("/admin")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<BundleResponse>> createAdmin(
            @Valid @RequestBody CreateBundleRequest payload, HttpServletRequest request) {
        validateQuery(request, Set.of());
        var detail = bundleService.createBundle(payload);
        return ResponseEntity.created(URI.create("/api/v1/catalog/bundle/admin/" + detail.bundleId()))
                .body(ApiResponse.ok(detail, "Đã tạo combo."));
    }

    @PutMapping("/admin/{bundleId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<BundleResponse> updateAdmin(
            @PathVariable UUID bundleId,
            @Valid @RequestBody UpdateBundleRequest payload,
            HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(bundleService.updateBundle(bundleId, payload));
    }

    @DeleteMapping("/admin/{bundleId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<Void> deleteAdmin(
            @PathVariable UUID bundleId,
            @RequestParam long expectedVersion,
            HttpServletRequest request) {
        validateQuery(request, Set.of("expectedVersion"));
        bundleService.deleteBundle(bundleId, expectedVersion);
        return ApiResponse.ok(null, "Đã xóa combo.");
    }

    @PatchMapping("/admin/{bundleId}/status")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<BundleResponse> statusAdmin(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangeBundleStatusRequest payload,
            HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(bundleService.changeProductStatus(bundleId, payload));
    }

    @PatchMapping("/admin/{bundleId}/publication-status")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<BundleResponse> publicationAdmin(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangePublicationStatusRequest payload,
            HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(bundleService.changePublicationStatus(bundleId, payload));
    }

    @PostMapping({ "/admin/{bundleId}/publish", "/admin/{bundleId}/unpublish" })
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<Void> retiredPublicationRouteAdmin(@PathVariable UUID bundleId) {
        throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    @GetMapping("/admin/skus")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<PageResponse<BundleSkuResponse>> searchSkusAdmin(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        validateQuery(request, Set.of("page", "size", "keyword"));
        return ApiResponse.ok(bundleService.searchSkus(page, size, keyword));
    }

    private void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().entrySet().stream()
                .anyMatch(p -> !allowed.contains(p.getKey()) || p.getValue().length != 1)) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
    }
}
