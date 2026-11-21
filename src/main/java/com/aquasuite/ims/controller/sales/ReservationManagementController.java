package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.response.ReservationManagementView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView.ReleaseResult;
import com.aquasuite.ims.service.sales.ReservationManagementService;
import com.aquasuite.ims.common.ApiResponse;
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
