package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest;
import com.aquasuite.ims.dto.sales.request.ProjectDepositRequests.*;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.dto.sales.response.ProjectDepositView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView;
import com.aquasuite.ims.dto.sales.response.ReservationManagementView.ReleaseResult;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.service.sales.OrderReservationService;
import com.aquasuite.ims.service.sales.impl.ProjectDepositService;
import com.aquasuite.ims.service.sales.helper.ReservationAllocationRecovery;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Controller Quản lý Giữ hàng tồn kho, Lịch sử giải phóng và Đặt cọc dự án.
 */
@RestController
public class OrderReservationController {

    private final OrderReservationService orderReservationService;
    private final ReservationAllocationRecovery allocationRecovery;
    private final ProjectDepositService projectDepositService;

    @Autowired
    public OrderReservationController(
            OrderReservationService orderReservationService,
            ReservationAllocationRecovery allocationRecovery,
            @Autowired(required = false) ProjectDepositService projectDepositService) {
        this.orderReservationService = orderReservationService;
        this.allocationRecovery = allocationRecovery;
        this.projectDepositService = projectDepositService;
    }

    private ProjectDepositService depositService() {
        if (projectDepositService == null) {
            throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND, "UC-29 demo profile is not active");
        }
        return projectDepositService;
    }

    // ── Stock Reservation endpoints (/api/v1/sales/orders/{orderId}/stock-reservations) ─

    @GetMapping("/api/v1/sales/orders/{orderId}/stock-reservations")
    public ApiResponse<OrderStockReservationView> getStockReservation(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderReservationService.get(orderId));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/stock-reservations/requests")
    public ApiResponse<OrderStockReservationView> requestStockReservation(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderStockReservationRequest request) {
        return ApiResponse.ok(orderReservationService.apply(orderId, request, false));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/stock-reservations/synchronize")
    public ApiResponse<OrderStockReservationView> synchronizeStockReservation(
            @PathVariable UUID orderId,
            @Valid @RequestBody OrderStockReservationRequest request) {
        return ApiResponse.ok(orderReservationService.apply(orderId, request, true));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/stock-reservations/allocations/{allocationId}/retry")
    public ApiResponse<OrderStockReservationView> retryStockAllocation(
            @PathVariable UUID orderId, @PathVariable UUID allocationId) {
        return ApiResponse.ok(allocationRecovery.retry(orderId, allocationId));
    }

    // ── Reservation Management History & Release ────────────────────────────

    @GetMapping("/api/v1/sales/orders/{orderId}/stock-reservations/history")
    public ApiResponse<ReservationManagementView> getReservationHistory(@PathVariable UUID orderId) {
        return ApiResponse.ok(orderReservationService.history(orderId));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/stock-reservations/releases/{decisionId}")
    public ApiResponse<ReleaseResult> releaseStockReservation(
            @PathVariable UUID orderId, @PathVariable UUID decisionId) {
        return ApiResponse.ok(orderReservationService.release(orderId, decisionId, null));
    }

    // ── Project Deposit endpoints (/api/v1/sales/orders/{orderId}/deposits-reservations) ─

    @GetMapping("/api/v1/sales/orders/{orderId}/deposits-reservations")
    public ApiResponse<ProjectDepositView> getProjectDeposit(@PathVariable UUID orderId) {
        return ApiResponse.ok(depositService().get(orderId));
    }

    @PutMapping("/api/v1/sales/orders/{orderId}/deposits-reservations/delivery-schedule")
    public ApiResponse<ProjectDepositView> scheduleProjectDepositDelivery(
            @PathVariable UUID orderId, @Valid @RequestBody Schedule request) {
        return ApiResponse.ok(depositService().updateSchedule(orderId, request));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/deposits-reservations/requests")
    public ApiResponse<ProjectDepositView> requestProjectDepositReservation(
            @PathVariable UUID orderId, @Valid @RequestBody Selection request) {
        return ApiResponse.ok(depositService().requestReservation(orderId, request));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/deposits-reservations/deposits")
    public ApiResponse<ProjectDepositView> recordProjectDeposit(
            @PathVariable UUID orderId, @Valid @RequestBody Deposit request) {
        return ApiResponse.ok(depositService().deposit(orderId, request));
    }

    @PostMapping("/api/v1/sales/orders/{orderId}/deposits-reservations/synchronize")
    public ApiResponse<ProjectDepositView> synchronizeProjectDepositReservations(
            @PathVariable UUID orderId, @Valid @RequestBody Hold request) {
        return ApiResponse.ok(depositService().synchronizeReservations(orderId, request));
    }

    @PostMapping("/api/v1/sales/deposit-reservation-orders")
    public ApiResponse<ProjectDepositView> createDepositReservationOrder(
            @Valid @RequestBody CreateOrderDeposit request) {
        return ApiResponse.ok(depositService().createAndDeposit(request));
    }
}
