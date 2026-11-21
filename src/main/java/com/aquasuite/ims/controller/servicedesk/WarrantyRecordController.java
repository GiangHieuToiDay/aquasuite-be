package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.request.VoidWarrantyRecordRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicySummaryResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Controller Quản lý thẻ bảo hành điện tử (UC-45) dành cho nhân viên và chủ cửa hàng.
 */
@RestController
@RequestMapping("/api/v1/warranty-records")
@RequiredArgsConstructor
public class WarrantyRecordController {

    private final WarrantyRecordService warrantyRecordService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyRecordListItemResponse>>> searchRecords(
            WarrantyRecordFilterRequest filter
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.searchRecords(filter)));
    }

    @GetMapping("/policies")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<List<WarrantyPolicySummaryResponse>>> getActivePolicies() {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getActivePolicies()));
    }

    @GetMapping("/variants/{variantId}/policy")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<CatalogWarrantyPolicyDto>> getVariantWarrantyInfo(
            @PathVariable UUID variantId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getVariantWarrantyInfo(variantId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> getRecordDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getRecordDetail(id)));
    }

    @PostMapping("/from-order")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> createFromOrder(
            @Valid @RequestBody CreateWarrantyFromOrderRequest request
    ) {
        WarrantyRecordDetailResponse created = warrantyRecordService.createFromOrder(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã cấp thẻ bảo hành từ đơn hàng."));
    }

    @PostMapping("/ad-hoc")
    @PreAuthorize("hasAnyRole('SHOP_OWNER', 'SALES_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> createAdHoc(
            @Valid @RequestBody CreateAdHocWarrantyRequest request
    ) {
        WarrantyRecordDetailResponse created = warrantyRecordService.createAdHoc(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Đã cấp thẻ bảo hành điện tử."));
    }

    @PostMapping("/{id}/void")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> voidRecord(
            @PathVariable UUID id,
            @Valid @RequestBody VoidWarrantyRecordRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(
                warrantyRecordService.voidRecord(id, request.reason()),
                "Đã hủy thẻ bảo hành."));
    }
}
