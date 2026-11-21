package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.iam.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantResponse;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.iam.UserAccountService;
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

    private final UserAccountService service;
    private final IamQueryService iamApi;

    @Deprecated(since = "UC-24 credit profile approval workflow")
    @PostMapping
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<CreditApprovalGrantResponse> issue(
            @Valid @RequestBody CreateCreditApprovalGrantRequest request) {
        return ApiResponse.ok(service.issue(iamApi.getCurrentUserId(), request),
                "Store Owner credit approval granted.");
    }
}
