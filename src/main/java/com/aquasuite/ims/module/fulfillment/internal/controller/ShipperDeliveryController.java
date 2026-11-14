package com.aquasuite.ims.module.fulfillment.internal.controller;

import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests;
import com.aquasuite.ims.module.fulfillment.internal.payload.response.DeliveryView;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import java.util.*;

@RestController
@RequestMapping("/api/v1/fulfillment")
@PreAuthorize("hasRole('SHIPPER')")
@RequiredArgsConstructor
public class ShipperDeliveryController {
    private final DeliveryService service;

    @GetMapping("/me/deliveries")
    public ApiResponse<Page<DeliveryView.Summary>> mine(@RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledFrom, @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate scheduledTo,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.ok(service.mine(status, scheduledFrom, scheduledTo, pageable));
    }

    @PostMapping("/deliveries/{id}/start")
    public ApiResponse<DeliveryView.Summary> start(@PathVariable UUID id) { return ApiResponse.ok(service.start(id)); }

    @PostMapping("/deliveries/{id}/reschedule")
    public ApiResponse<DeliveryView.Summary> reschedule(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Reschedule request) {
        return ApiResponse.ok(service.reschedule(id, request));
    }

    @PostMapping(value = "/deliveries/{id}/complete", consumes = "multipart/form-data")
    public ApiResponse<DeliveryView.Summary> complete(@PathVariable UUID id,
            @RequestPart(value = "photos", required = false) List<MultipartFile> photos) {
        return ApiResponse.ok(service.complete(id, photos));
    }

    @PostMapping("/deliveries/{id}/fail")
    public ApiResponse<DeliveryView.Summary> fail(@PathVariable UUID id, @Valid @RequestBody DeliveryRequests.Fail request) {
        return ApiResponse.ok(service.fail(id, request));
    }
}
