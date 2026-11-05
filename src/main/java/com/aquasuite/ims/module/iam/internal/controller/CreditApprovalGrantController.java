package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.CreditApprovalGrantResponse;
import com.aquasuite.ims.module.iam.internal.service.CreditApprovalGrantService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/iam/credit-approval-grants")
public class CreditApprovalGrantController {
    private final CreditApprovalGrantService service;
    private final IamApi iamApi;

    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<CreditApprovalGrantResponse> issue(@Valid @RequestBody CreateCreditApprovalGrantRequest request) {
        return ApiResponse.ok(service.issue(iamApi.getCurrentUserId(), request), "Store Owner credit approval granted.");
    }
}
