package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.crm.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestDetailResponse;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.enums.crm.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.service.crm.CustomerCreditProfileService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/crm/credit-profile-requests")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class CustomerCreditProfileRequestController {

    private final CustomerCreditProfileService customerCreditProfileService;

    @GetMapping
    public ApiResponse<PageResponse<CustomerCreditProfileRequestResponse>> list(
            @RequestParam(required = false) CustomerCreditProfileRequestStatus status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponse.ok(PageResponse.from(customerCreditProfileService.list(status, pageable)));
    }

    @GetMapping("/{requestId}")
    public ApiResponse<CustomerCreditProfileRequestDetailResponse> detail(@PathVariable UUID requestId) {
        return ApiResponse.ok(customerCreditProfileService.detail(requestId));
    }

    @GetMapping("/{requestId}/identity-document")
    public ResponseEntity<byte[]> identityDocument(@PathVariable UUID requestId) {
        var document = customerCreditProfileService.readIdentityDocument(requestId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + document.fileName() + "\"")
                .contentType(MediaType.parseMediaType(document.contentType()))
                .body(document.content());
    }

    @PostMapping("/{requestId}/approve")
    public ResponseEntity<ApiResponse<CustomerCreditProfileRequestResponse>> approve(
            @PathVariable UUID requestId,
            @Valid @RequestBody ApproveCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerCreditProfileService.approve(requestId, request), "Credit profile request approved."));
    }

    @PostMapping("/{requestId}/reject")
    public ResponseEntity<ApiResponse<CustomerCreditProfileRequestResponse>> reject(
            @PathVariable UUID requestId,
            @Valid @RequestBody RejectCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(
                customerCreditProfileService.reject(requestId, request), "Credit profile request rejected."));
    }
}
