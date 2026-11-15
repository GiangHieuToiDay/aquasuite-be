package com.aquasuite.ims.module.fulfillment.internal.controller;

import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests;
import com.aquasuite.ims.module.fulfillment.internal.payload.response.DeliveryView;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.*;

@RestController
@RequestMapping("/api/v1/fulfillment")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService service;

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<DeliveryView.Summary>> search(@RequestParam(required = false) String status,
            @RequestParam(required = false) UUID shipperId, @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String deliveryCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledFrom, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(status, shipperId, orderId, deliveryCode, scheduledFrom, scheduledTo, pageable));
    }

    @GetMapping("/deliveries/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ApiResponse<DeliveryView.Detail> detail(@PathVariable UUID id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/orders/{orderId}/deliveries")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<DeliveryView.Summary>> byOrder(@PathVariable UUID orderId, @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.byOrder(orderId, pageable));
    }

    @PutMapping("/delivery-assignments")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<List<DeliveryView.Summary>> assign(@Valid @RequestBody DeliveryRequests.Assign request) {
        return ApiResponse.ok(service.assign(request));
    }

    @DeleteMapping("/deliveries/{id}/assignment")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<DeliveryView.Summary> unassign(@PathVariable UUID id) { return ApiResponse.ok(service.unassign(id)); }

    @GetMapping("/deliveries/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        var photo = service.readPhoto(id, photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }
}
