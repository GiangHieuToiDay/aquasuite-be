package com.aquasuite.ims.controller.finance;

import com.aquasuite.ims.dto.finance.request.CreditCheckRequest;
import com.aquasuite.ims.dto.finance.request.DebtCallResultRequest;
import com.aquasuite.ims.dto.finance.request.DebtReminderRequest;
import com.aquasuite.ims.dto.finance.request.ReasonRequest;
import com.aquasuite.ims.dto.finance.response.StatementDocument;
import com.aquasuite.ims.dto.finance.response.*;
import com.aquasuite.ims.service.finance.CustomerDebtService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class CustomerDebtController {
    private final CustomerDebtService service;
    private final IamQueryService iamApi;

    // ── Customer Debts (/api/v1/finance/customer-debts) ──────────────────────

    @GetMapping("/api/v1/finance/customer-debts")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<CustomerDebtPageResponse> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String customerType,
            @RequestParam(required = false) String debtStatus,
            @RequestParam(defaultValue = "customerCode") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.list(keyword, customerType, debtStatus, sortBy, direction, page, size));
    }

    @GetMapping("/api/v1/finance/customer-debts/stats")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<DebtDashboardStatsResponse> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping("/api/v1/finance/customer-debts/{customerId}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<CustomerDebtDetailResponse> detail(@PathVariable UUID customerId) {
        return ApiResponse.ok(service.detail(customerId));
    }

    @GetMapping("/api/v1/finance/customer-debts/{customerId}/blacklist-status")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<BlacklistStatusResponse> blacklistStatus(@PathVariable UUID customerId) {
        return ApiResponse.ok(service.blacklistStatus(customerId));
    }

    @PostMapping("/api/v1/finance/customer-debts/{customerId}/credit-check")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<CreditCheckResponse> checkCredit(@PathVariable UUID customerId, @Valid @RequestBody CreditCheckRequest request) {
        return ApiResponse.ok(service.checkCredit(customerId, request));
    }

    @PostMapping("/api/v1/finance/customer-debts/orders/{orderId}/credit-override")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ApiResponse<Void> approveOverride(@PathVariable UUID orderId, @Valid @RequestBody ReasonRequest request) {
        service.approveOverride(orderId, request, iamApi.getCurrentUserId());
        return ApiResponse.ok(null, "Credit override approved for this order.");
    }

    @PostMapping("/api/v1/finance/customer-debts/{customerId}/reminders")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<DebtReminderResponse> sendReminder(@PathVariable UUID customerId, @Valid @RequestBody DebtReminderRequest request) {
        return ApiResponse.ok(service.sendReminder(customerId, request, iamApi.getCurrentUserId()));
    }

    @PostMapping("/api/v1/finance/customer-debts/{customerId}/reminder-calls")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Void> saveCallResult(@PathVariable UUID customerId, @Valid @RequestBody DebtCallResultRequest request) {
        service.saveCallResult(customerId, request, iamApi.getCurrentUserId());
        return ApiResponse.ok(null, "Debt call result saved successfully.");
    }

    @GetMapping("/api/v1/finance/customer-debts/{customerId}/statement")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ResponseEntity<byte[]> exportStatement(@PathVariable UUID customerId) {
        StatementDocument document = service.exportStatement(customerId, iamApi.getCurrentUserId());
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + document.fileName() + "\"")
                .contentType(MediaType.APPLICATION_PDF).body(document.bytes());
    }

    @PostMapping("/api/v1/finance/customer-debts/{customerId}/statement/email")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<StatementEmailResponse> emailStatement(@PathVariable UUID customerId) {
        return ApiResponse.ok(service.emailStatement(customerId, iamApi.getCurrentUserId()));
    }

    @PatchMapping("/api/v1/finance/customer-debts/{customerId}/block")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> block(@PathVariable UUID customerId, @Valid @RequestBody ReasonRequest request) {
        service.block(customerId, request, iamApi.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Customer blocked successfully."));
    }

    @PatchMapping("/api/v1/finance/customer-debts/{customerId}/restore")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> restore(@PathVariable UUID customerId, @Valid @RequestBody ReasonRequest request) {
        service.restore(customerId, request, iamApi.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(null, "Customer restored successfully."));
    }

    // ── Credit Control (from CreditControlController) ────────────────────────

    @GetMapping("/api/v1/finance/credit-control/customers/{customerId}/summary")
    @PreAuthorize("hasAnyRole('SALES_STAFF','ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<CustomerCreditSummaryResponse> customerSummary(@PathVariable UUID customerId) {
        return ApiResponse.ok(service.creditSummary(customerId));
    }

    @GetMapping("/api/v1/finance/customer-debts/me")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<CustomerDebtDetailResponse> myDebtDetail() {
        return ApiResponse.ok(service.myDebtDetail(iamApi.getCurrentUserId()));
    }
}
