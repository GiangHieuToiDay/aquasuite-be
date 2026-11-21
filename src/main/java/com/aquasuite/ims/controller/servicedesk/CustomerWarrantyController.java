package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyRecordService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Cổng tra cứu thẻ bảo hành điện tử cá nhân dành cho khách hàng (UC-45).
 */
@RestController
@RequestMapping("/api/v1/storefront/warranty-records")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerWarrantyController {

    private final WarrantyRecordService warrantyRecordService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<WarrantyRecordListItemResponse>>> listMyWarranties(
            WarrantyRecordFilterRequest filter
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getMyRecords(filter)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<WarrantyRecordDetailResponse>> getMyWarrantyDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyRecordService.getMyRecordDetail(id)));
    }
}

