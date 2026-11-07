package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.payload.response.CustomerCreditSummaryResponse;
import com.aquasuite.ims.module.finance.internal.service.CustomerDebtService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/** Public HTTP boundary used by UC-21 before a sales order is created. */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/finance/credit-control")
public class CreditControlController {
    private final CustomerDebtService customerDebtService;

    @GetMapping("/customers/{customerId}/summary")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<CustomerCreditSummaryResponse> customerSummary(@PathVariable UUID customerId) {
        return ApiResponse.ok(customerDebtService.creditSummary(customerId));
    }
}
