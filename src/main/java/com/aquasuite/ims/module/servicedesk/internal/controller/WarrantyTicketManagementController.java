package com.aquasuite.ims.module.servicedesk.internal.controller;

import com.aquasuite.ims.module.servicedesk.internal.payload.req.DispatchWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.RejectWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.WarrantyTicketFilterRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketListItemResponse;
import com.aquasuite.ims.module.servicedesk.internal.service.WarrantyTicketManagementService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Quản lý yêu cầu bảo hành (ticket, UC-47) phía nội bộ cửa hàng và bắn công việc kỹ thuật (FE-13 Giai đoạn 2b).
 * <ul>
 *   <li>Chủ cửa hàng/Kỹ thuật viên: tra cứu danh sách và chi tiết ticket.</li>
 *   <li>Chủ cửa hàng: "bắn job" sửa chữa bảo hành cho kỹ thuật viên ({@code POST /{id}/dispatch}).</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/warranty-tickets")
@RequiredArgsConstructor
public class WarrantyTicketManagementController {

    private final WarrantyTicketManagementService warrantyTicketManagementService;

    /** Danh sách yêu cầu bảo hành (toàn bộ khách), có phân trang và lọc theo trạng thái. */
    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyTicketListItemResponse>>> list(
            WarrantyTicketFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketManagementService.list(filter)));
    }

    /** Chi tiết một yêu cầu bảo hành. */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketManagementService.getById(id)));
    }

    /** Bắn công việc sửa chữa bảo hành cho kỹ thuật viên (thao tác của Chủ cửa hàng). */
    @PostMapping("/{id}/dispatch")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> dispatch(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchWarrantyTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketManagementService.dispatch(id, request),
                "Đã bắn công việc sửa chữa bảo hành cho kỹ thuật viên"));
    }

    /** Từ chối yêu cầu bảo hành tại bàn, kèm lý do (thao tác của Chủ cửa hàng). */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectWarrantyTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketManagementService.reject(id, request),
                "Đã từ chối yêu cầu bảo hành"));
    }
}
