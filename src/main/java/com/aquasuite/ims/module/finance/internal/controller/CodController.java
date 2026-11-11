package com.aquasuite.ims.module.finance.internal.controller;

import com.aquasuite.ims.module.finance.internal.payload.CodPayload.*;
import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.List;
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

    @PostMapping("/collections/{id}/accept")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<Collection> accept(@PathVariable UUID id,@RequestBody @Valid Review data) {
        return ApiResponse.ok(service.review(id,data,true));
    }
    @PostMapping("/collections/{id}/reject")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<Collection> reject(@PathVariable UUID id,@RequestBody @Valid Review data) {
        return ApiResponse.ok(service.review(id,data,false));
    }
    @PostMapping("/remittances")
    @PreAuthorize("hasRole('ACCOUNTANT')")
    public ApiResponse<Receipt> remit(@RequestBody @Valid Remit data) { return ApiResponse.ok(service.remit(data)); }

    @GetMapping("/collections/{id}/remittances")
    public ApiResponse<List<Receipt>> history(@PathVariable UUID id) { return ApiResponse.ok(service.history(id)); }

    @GetMapping("/orders/{orderId}/balance")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SALES_STAFF','SHOP_OWNER')")
    public ApiResponse<Balance> balance(@PathVariable UUID orderId) { return ApiResponse.ok(service.balance(orderId)); }
}
