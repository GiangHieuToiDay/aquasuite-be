package com.aquasuite.ims.module.servicedesk.internal.controller;

import com.aquasuite.ims.module.servicedesk.internal.payload.req.CancelWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.WarrantyTicketFilterRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.ClaimFormResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketListItemResponse;
import com.aquasuite.ims.module.servicedesk.internal.service.WarrantyTicketService;
import com.aquasuite.ims.module.servicedesk.internal.storage.StoredMedia;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Cổng tiếp nhận và quản lý yêu cầu bảo hành trực tuyến dành cho khách hàng (UC-47).
 */
@RestController
@RequestMapping("/api/v1/storefront")
@PreAuthorize("hasRole('CUSTOMER')")
@RequiredArgsConstructor
public class CustomerWarrantyTicketController {

    private final WarrantyTicketService warrantyTicketService;

    @GetMapping("/warranty-records/{id}/claim-form")
    public ResponseEntity<ApiResponse<ClaimFormResponse>> getClaimForm(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.getClaimForm(id)));
    }

    @PostMapping(value = "/warranty-tickets/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<StoredMedia>> uploadMedia(@RequestPart("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.uploadMedia(file)));
    }

    @PostMapping("/warranty-tickets")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> submitTicket(
            @Valid @RequestBody CreateWarrantyTicketRequest request
    ) {
        WarrantyTicketDetailResponse response = warrantyTicketService.submit(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "MSG85: Yêu cầu bảo hành đã được gửi. Đội kỹ thuật sẽ liên hệ bạn trong vòng 4 giờ làm việc."));
    }

    @GetMapping("/warranty-tickets")
    public ResponseEntity<ApiResponse<PageResponse<WarrantyTicketListItemResponse>>> listMyTickets(
            WarrantyTicketFilterRequest filter
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.listMine(filter)));
    }

    @GetMapping("/warranty-tickets/{id}")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> getMyTicket(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.getMine(id)));
    }

    @PostMapping("/warranty-tickets/{id}/cancel")
    public ResponseEntity<ApiResponse<WarrantyTicketDetailResponse>> cancelTicket(
            @PathVariable UUID id,
            @RequestBody(required = false) CancelWarrantyTicketRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(warrantyTicketService.cancel(id, request), "Hủy yêu cầu bảo hành thành công."));
    }
}
