package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest;
import com.aquasuite.ims.dto.sales.response.OrderStockReservationView;
import com.aquasuite.ims.service.sales.OrderStockReservationService;
import com.aquasuite.ims.service.sales.helper.ReservationAllocationRecovery;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/sales/orders/{orderId}/stock-reservations")
public class OrderStockReservationController {
    private final OrderStockReservationService service;
    private final ReservationAllocationRecovery recovery;
    @GetMapping
    public ApiResponse<OrderStockReservationView> get(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.get(orderId));
    }
    @PostMapping("/requests")
    public ApiResponse<OrderStockReservationView> request(@PathVariable UUID orderId,
            @Valid @RequestBody OrderStockReservationRequest request) {
        return ApiResponse.ok(service.apply(orderId,request,false));
    }
    @PostMapping("/synchronize")
    public ApiResponse<OrderStockReservationView> synchronize(@PathVariable UUID orderId,
            @Valid @RequestBody OrderStockReservationRequest request) {
        return ApiResponse.ok(service.apply(orderId,request,true));
    }
    @PostMapping("/allocations/{allocationId}/retry")
    public ApiResponse<OrderStockReservationView> retry(@PathVariable UUID orderId,@PathVariable UUID allocationId) {
        return ApiResponse.ok(recovery.retry(orderId,allocationId));
    }
}
