package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView;
import com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView.ReleaseResult;
import com.aquasuite.ims.module.sales.internal.service.ReservationManagementService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales/orders/{orderId}/stock-reservations")
public class ReservationManagementController {
    private final ReservationManagementService service;

    @GetMapping("/history")
    public ApiResponse<ReservationManagementView> history(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.history(orderId));
    }

    @PostMapping("/releases/{decisionId}")
    public ApiResponse<ReleaseResult> release(@PathVariable UUID orderId, @PathVariable UUID decisionId) {
        return ApiResponse.ok(service.release(orderId, decisionId));
    }
}
