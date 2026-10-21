package com.aquasuite.ims.module.catalog.internal.variant;

import com.aquasuite.ims.module.catalog.internal.entity.enums.VariantStatus;
import com.aquasuite.ims.module.catalog.internal.variant.dto.*;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/{productId}/variants")
public class VariantController {
    private final VariantService service;

    @GetMapping
    public ApiResponse<Page<VariantResponse>> list(@PathVariable UUID productId, @RequestParam(required = false) String keyword, @RequestParam(required = false) VariantStatus status, Pageable pageable) {
        return ApiResponse.ok(service.list(productId, keyword, status, pageable));
    }

    @GetMapping("/stats")
    public ApiResponse<VariantStatsResponse> stats(@PathVariable UUID productId) {
        return ApiResponse.ok(service.stats(productId));
    }

    @PostMapping
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ApiResponse<VariantResponse> create(@PathVariable UUID productId, @Valid @RequestBody VariantUpsertRequest request) {
        return ApiResponse.ok(service.create(productId, request), "SKU and variant pricing saved successfully.");
    }

    @PostMapping("/matrix")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ApiResponse<List<VariantResponse>> matrix(@PathVariable UUID productId, @Valid @RequestBody VariantMatrixRequest request) {
        return ApiResponse.ok(service.createMatrix(productId, request), "Generated " + request.variants().size() + " variants successfully.");
    }

    @PutMapping("/{variantId}")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ApiResponse<VariantResponse> update(@PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody VariantUpsertRequest request) {
        return ApiResponse.ok(service.update(productId, variantId, request));
    }

    @PatchMapping("/{variantId}/status")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ApiResponse<VariantResponse> status(@PathVariable UUID productId, @PathVariable UUID variantId, @Valid @RequestBody VariantStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(productId, variantId, request));
    }

    @GetMapping("/{variantId}/deletion-eligibility")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ApiResponse<VariantDeletionEligibilityResponse> eligibility(@PathVariable UUID productId, @PathVariable UUID variantId) {
        return ApiResponse.ok(service.deletionEligibility(productId, variantId));
    }

    @DeleteMapping("/{variantId}")
    @PreAuthorize("hasRole('STORE_OWNER')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID productId, @PathVariable UUID variantId, @RequestBody VariantDeleteRequest request) {
        service.delete(productId, variantId, request);
        return ResponseEntity.ok(ApiResponse.ok(null));
    }

    @ExceptionHandler(VariantDeleteBlockedException.class)
    public ResponseEntity<ApiResponse<VariantDeletionEligibilityResponse>> blocked(VariantDeleteBlockedException exception) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.<VariantDeletionEligibilityResponse>builder()
                .success(false).code(CatalogVariantErrorCode.DELETE_BLOCKED.getCode()).message(CatalogVariantErrorCode.DELETE_BLOCKED.getMessage())
                .data(exception.getEligibility()).build());
    }
}
