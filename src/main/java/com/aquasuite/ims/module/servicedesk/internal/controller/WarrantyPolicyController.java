package com.aquasuite.ims.module.servicedesk.internal.controller;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyPolicyStatus;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyReferenceDataResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyResponse;
import com.aquasuite.ims.module.servicedesk.internal.service.WarrantyPolicyService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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
import com.aquasuite.ims.share.dto.PageResponse;

/** UC-44: policy definitions only; warranty record issuance belongs to UC-45. */
@RestController
@RequestMapping("/api/v1/warranty-policies")
@RequiredArgsConstructor
public class WarrantyPolicyController {

    private final WarrantyPolicyService warrantyPolicyService;

    @GetMapping
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<PageResponse<WarrantyPolicyResponse>> search(@RequestParam(required = false) String keyword,
                                                              @RequestParam(required = false) WarrantyPolicyStatus status,
                                                              @PageableDefault(size = 20, sort = "created_at", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(warrantyPolicyService.search(keyword, status, pageable));
    }

    @GetMapping("/reference-data")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<WarrantyPolicyReferenceDataResponse> referenceData() {
        return ApiResponse.ok(warrantyPolicyService.referenceData());
    }

    @GetMapping("/{policyId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<WarrantyPolicyDetailResponse> detail(@PathVariable UUID policyId) {
        return ApiResponse.ok(warrantyPolicyService.detail(policyId));
    }

    @PostMapping
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyPolicyResponse>> create(@Valid @RequestBody UpsertWarrantyPolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(warrantyPolicyService.create(request), "Warranty policy created successfully."));
    }

    @PutMapping("/{policyId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<WarrantyPolicyResponse> update(@PathVariable UUID policyId,
                                                        @Valid @RequestBody UpsertWarrantyPolicyRequest request) {
        return ApiResponse.ok(warrantyPolicyService.update(policyId, request), "Warranty policy updated successfully.");
    }

    @PostMapping("/{policyId}/deactivate")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<WarrantyPolicyResponse> deactivate(@PathVariable UUID policyId) {
        return ApiResponse.ok(warrantyPolicyService.deactivate(policyId), "Warranty policy deactivated successfully.");
    }

    @DeleteMapping("/{policyId}")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<Void> delete(@PathVariable UUID policyId) {
        warrantyPolicyService.delete(policyId);
        return ApiResponse.ok(null, "Warranty policy deleted successfully.");
    }
}
