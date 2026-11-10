package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.module.crm.internal.payload.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestDetailResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileRequestService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/credit-profile-requests")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class CustomerCreditProfileRequestController {
    private final CustomerCreditProfileRequestService service;

    @GetMapping
    public ApiResponse<PageResponse<CustomerCreditProfileRequestResponse>> list(
            @RequestParam(required = false) CustomerCreditProfileRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(service.list(status, pageable)));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<CustomerCreditProfileRequestDetailResponse> detail(@PathVariable UUID requestId) {
        return ApiResponse.ok(service.detail(requestId));
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<CustomerCreditProfileRequestResponse>> approve(
            @PathVariable UUID requestId,
            @Valid @RequestBody ApproveCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.approve(requestId, request), "Credit profile request approved."));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<CustomerCreditProfileRequestResponse>> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.reject(requestId, request), "Credit profile request rejected."));
    }
}
