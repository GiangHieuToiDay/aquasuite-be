package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.request.VoidWarrantyRecordRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicySummaryResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyAutoIssuanceService;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller Quản lý thẻ bảo hành điện tử (UC-45) dành cho nhân viên, chủ cửa hàng và khách hàng.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class WarrantyRecordController {

    private final WarrantyRecordService warrantyRecordService;

    @Autowired(required = false)
    private WarrantyAutoIssuanceService warrantyAutoIssuanceService;

    // ── Staff & Shop Owner endpoints ─────────────────────────────────────────

    @GetMapping("/api/v1/warranty-records")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyRecordListItemResponse>>> searchRecords(
            @Valid WarrantyRecordFilterRequest filter
    ) {
        syncAutoWarrantiesSafely();
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.searchRecords(filter)));
    }

    @GetMapping("/api/v1/warranty-records/policies")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<List<WarrantyPolicySummaryResponse>>> getActivePolicies() {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getActivePolicies()));
    }

    @GetMapping("/api/v1/warranty-records/variants/{variantId}/policy")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<CatalogWarrantyPolicyDto>> getVariantWarrantyInfo(
            @PathVariable UUID variantId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getVariantWarrantyInfo(variantId)));
    }

    @GetMapping("/api/v1/warranty-records/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> getRecordDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getRecordDetail(id)));
    }

    @PostMapping("/api/v1/warranty-records/from-order")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> createFromOrder(
            @Valid @RequestBody CreateWarrantyFromOrderRequest request
    ) {
        WarrantyRecordDetailResponse created = warrantyRecordService.createFromOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã cấp thẻ bảo hành từ đơn hàng."));
    }

    @PostMapping("/api/v1/warranty-records/ad-hoc")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> createAdHoc(
            @Valid @RequestBody CreateAdHocWarrantyRequest request
    ) {
        WarrantyRecordDetailResponse created = warrantyRecordService.createAdHoc(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã cấp thẻ bảo hành điện tử."));
    }

    @PostMapping("/api/v1/warranty-records/{id}/void")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> voidRecord(
            @PathVariable UUID id,
            @Valid @RequestBody VoidWarrantyRecordRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                warrantyRecordService.voidRecord(id, request.reason()),
                "Đã hủy thẻ bảo hành."));
    }

    // ── Storefront Customer records (from CustomerWarrantyController) ────────

    @GetMapping("/api/v1/storefront/warranty-records")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyRecordListItemResponse>>> listMyWarranties(
            @Valid WarrantyRecordFilterRequest filter
    ) {
        syncAutoWarrantiesSafely();
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getMyRecords(filter)));
    }

    @GetMapping("/api/v1/storefront/warranty-records/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> getMyWarrantyDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getMyRecordDetail(id)));
    }

    // ── Customer Warranty Lookup (from WarrantyLookupController) ─────────────

    @GetMapping("/api/v1/customer/warranties")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<WarrantyLookupResponse>> findMine(@RequestParam(required = false) String keyword) {
        syncAutoWarrantiesSafely();
        return ApiResponse.ok(warrantyRecordService.findMine(keyword));
    }

    private void syncAutoWarrantiesSafely() {
        if (warrantyAutoIssuanceService == null) {
            return;
        }
        try {
            warrantyAutoIssuanceService.syncEligibleRecords();
        } catch (RuntimeException ex) {
            log.warn("On-demand warranty auto-issuance skipped: {}", ex.getMessage());
        }
    }
}
