package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.*;
import com.aquasuite.ims.dto.servicedesk.response.*;
import com.aquasuite.ims.service.servicedesk.WarrantyTicketService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WarrantyTicketController {

    private final WarrantyTicketService warrantyTicketService;

    // ── Staff / Owner endpoints (from WarrantyTicketManagementController) ───

    @GetMapping("/api/v1/warranty-tickets")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyTicketListItemResponse>>> list(
            WarrantyTicketFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.list(filter)));
    }

    @GetMapping("/api/v1/warranty-tickets/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> getDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.getById(id)));
    }

    @PostMapping("/api/v1/warranty-tickets/{id}/dispatch")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> dispatch(
            @PathVariable UUID id,
            @Valid @RequestBody DispatchWarrantyTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.dispatch(id, request),
                "Đã bắn công việc sửa chữa bảo hành cho kỹ thuật viên"));
    }

    @PostMapping("/api/v1/warranty-tickets/{id}/reject")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> reject(
            @PathVariable UUID id,
            @Valid @RequestBody RejectWarrantyTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.reject(id, request),
                "Đã từ chối yêu cầu bảo hành"));
    }

    // ── Storefront Customer endpoints (from CustomerWarrantyTicketController) 

    @GetMapping("/api/v1/storefront/warranty-records/{id}/claim-form")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<ClaimFormResponse>> getClaimForm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.getClaimForm(id)));
    }

    @PostMapping(value = "/api/v1/storefront/warranty-tickets/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<StoredMedia>> uploadTicketMedia(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.uploadMedia(file)));
    }

    @PostMapping("/api/v1/storefront/warranty-tickets")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> submitTicket(
            @Valid @RequestBody CreateWarrantyTicketRequest request) {
        WarrantyTicketDetailResponse response = warrantyTicketService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "MSG85: Yêu cầu bảo hành đã được gửi. Đội kỹ thuật sẽ liên hệ bạn trong vòng 4 giờ làm việc."));
    }

    @GetMapping("/api/v1/storefront/warranty-tickets")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyTicketListItemResponse>>> listMyTickets(
            WarrantyTicketFilterRequest filter) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.listMine(filter)));
    }

    @GetMapping("/api/v1/storefront/warranty-tickets/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> getMyTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.getMine(id)));
    }

    @PostMapping("/api/v1/storefront/warranty-tickets/{id}/cancel")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> cancelTicket(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelWarrantyTicketRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.cancel(id, request), "Hủy yêu cầu bảo hành thành công."));
    }
}
