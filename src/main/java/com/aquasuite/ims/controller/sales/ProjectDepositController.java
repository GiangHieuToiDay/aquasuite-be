package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.ProjectDepositRequests.*;
import com.aquasuite.ims.dto.sales.response.ProjectDepositView;
import com.aquasuite.ims.service.sales.ProjectDepositService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@Profile("uc29-demo & !prod & !production")
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class ProjectDepositController {
    private static final String ORDER_PATH = "/orders/{orderId}/deposits-reservations";
    private final ProjectDepositService service;

    @GetMapping(ORDER_PATH)
    public ApiResponse<ProjectDepositView> get(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.get(orderId));
    }

    @PutMapping(ORDER_PATH + "/delivery-schedule")
    public ApiResponse<ProjectDepositView> schedule(@PathVariable UUID orderId, @Valid @RequestBody Schedule request) {
        return ApiResponse.ok(service.updateSchedule(orderId, request));
    }

    @PostMapping(ORDER_PATH + "/requests")
    public ApiResponse<ProjectDepositView> request(@PathVariable UUID orderId, @Valid @RequestBody Selection request) {
        return ApiResponse.ok(service.requestReservation(orderId, request));
    }

    @PostMapping(ORDER_PATH + "/deposits")
    public ApiResponse<ProjectDepositView> deposit(@PathVariable UUID orderId, @Valid @RequestBody Deposit request) {
        return ApiResponse.ok(service.deposit(orderId, request));
    }

    @PostMapping(ORDER_PATH + "/synchronize")
    public ApiResponse<ProjectDepositView> synchronize(@PathVariable UUID orderId, @Valid @RequestBody Hold request) {
        return ApiResponse.ok(service.synchronizeReservations(orderId, request));
    }

    @PostMapping("/deposit-reservation-orders")
    public ApiResponse<ProjectDepositView> create(@Valid @RequestBody CreateOrderDeposit request) {
        return ApiResponse.ok(service.createAndDeposit(request));
    }
}
