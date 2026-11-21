package com.aquasuite.ims.controller.finance;

import com.aquasuite.ims.dto.finance.response.CodPayload.*;
import com.aquasuite.ims.service.finance.CodService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/finance/cod")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SHIPPER','ACCOUNTANT','SALES_STAFF','SHOP_OWNER')")
public class CodController {
    private final CodService service;

    @PostMapping(value="/collections",consumes=MediaType.APPLICATION_JSON_VALUE)
    @PreAuthorize("hasRole('SHIPPER')")
    public ApiResponse<Collection> claim(@RequestBody @Valid Claim data) {
        return ApiResponse.ok(service.claim(data));
    }
    @GetMapping("/collections")
    public ApiResponse<Page<Collection>> list(@RequestParam(required=false) UUID shipperId,
            @RequestParam(required=false) UUID orderId, @RequestParam(required=false) String status,
            @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ApiResponse.ok(service.list(shipperId,orderId,status,page,size));
    }
    @GetMapping("/collections/{id}")
    public ApiResponse<Collection> detail(@PathVariable UUID id) { return ApiResponse.ok(service.detail(id)); }

    @GetMapping("/deliveries/{deliveryId}/claim-capacity")
    public ApiResponse<ClaimCapacity> claimCapacity(@PathVariable UUID deliveryId) {
        return ApiResponse.ok(service.claimCapacity(deliveryId));
    }

    @PostMapping("/collections/{id}/accept")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Collection> accept(@PathVariable UUID id,@RequestBody @Valid Review data) {
        return ApiResponse.ok(service.review(id,data,true));
    }
    @PostMapping("/collections/{id}/reject")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<Collection> reject(@PathVariable UUID id,@RequestBody @Valid Review data) {
        return ApiResponse.ok(service.review(id,data,false));
    }
    @GetMapping("/orders/{orderId}/balance")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Balance> balance(@PathVariable UUID orderId) { return ApiResponse.ok(service.balance(orderId)); }
}
