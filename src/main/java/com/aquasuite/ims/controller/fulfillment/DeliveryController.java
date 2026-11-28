package com.aquasuite.ims.controller.fulfillment;

import com.aquasuite.ims.service.fulfillment.DeliveryService;
import com.aquasuite.ims.dto.fulfillment.request.DeliveryRequests;
import com.aquasuite.ims.dto.fulfillment.response.DeliveryView;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/v1/fulfillment")
@RequiredArgsConstructor
public class DeliveryController {
    private final DeliveryService service;

    // ── Admin / Shop Owner endpoints ──────────────────────────────────────────

    @GetMapping("/deliveries")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<DeliveryView.Summary>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID shipperId,
            @RequestParam(required = false) UUID orderId,
            @RequestParam(required = false) String deliveryCode,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.search(status, shipperId, orderId, deliveryCode, scheduledFrom, scheduledTo, pageable));
    }

    @GetMapping("/deliveries/{id}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ApiResponse<DeliveryView.Detail> detail(@PathVariable UUID id) {
        return ApiResponse.ok(service.detail(id));
    }

    @GetMapping("/orders/{orderId}/deliveries")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Page<DeliveryView.Summary>> byOrder(@PathVariable UUID orderId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.byOrder(orderId, pageable));
    }

    @PutMapping("/delivery-assignments")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<List<DeliveryView.Summary>> assign(@Valid @RequestBody DeliveryRequests.Assign request) {
        return ApiResponse.ok(service.assign(request));
    }

    @DeleteMapping("/deliveries/{id}/assignment")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<DeliveryView.Summary> unassign(@PathVariable UUID id) {
        return ApiResponse.ok(service.unassign(id));
    }

    @GetMapping("/deliveries/{id}/photos/{photoId}")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ResponseEntity<byte[]> photo(@PathVariable UUID id, @PathVariable UUID photoId) {
        var photo = service.readPhoto(id, photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .body(photo.bytes());
    }

    // ── Shipper endpoints (from ShipperDeliveryController) ───────────────────

    @GetMapping("/me/deliveries")
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<Page<DeliveryView.Summary>> mine(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.mine(status, scheduledFrom, scheduledTo, pageable));
    }

    @PostMapping("/deliveries/{id}/start")
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<DeliveryView.Summary> start(@PathVariable UUID id) {
        return ApiResponse.ok(service.start(id));
    }

    @PostMapping("/deliveries/{id}/reschedule")
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<DeliveryView.Summary> reschedule(@PathVariable UUID id,
            @Valid @RequestBody DeliveryRequests.Reschedule request) {
        return ApiResponse.ok(service.reschedule(id, request));
    }

    @PostMapping(value = "/deliveries/{id}/complete", consumes = "multipart/form-data")
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<DeliveryView.Summary> complete(@PathVariable UUID id,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return ApiResponse.ok(service.complete(id, photos));
    }

    @PostMapping("/deliveries/{id}/fail")
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<DeliveryView.Summary> fail(@PathVariable UUID id,
            @Valid @RequestBody DeliveryRequests.Fail request) {
        return ApiResponse.ok(service.fail(id, request));
    }

    @GetMapping("/customer/deliveries")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<DeliveryView.CustomerTracking>> customerDeliveries() {
        return ApiResponse.ok(service.myCustomerDeliveries());
    }

    @GetMapping("/customer/deliveries/{id}/photos/{photoId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<byte[]> customerPhoto(@PathVariable UUID id, @PathVariable UUID photoId) {
        var photo = service.readCustomerPhoto(id, photoId);
        return ResponseEntity.ok().contentType(MediaType.parseMediaType(photo.mimeType()))
                .cacheControl(CacheControl.noStore()).body(photo.bytes());
    }
}
