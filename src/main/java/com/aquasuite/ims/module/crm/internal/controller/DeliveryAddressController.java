package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.service.DeliveryAddressService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profile/addresses")
@RequiredArgsConstructor
public class DeliveryAddressController {

    private final DeliveryAddressService deliveryAddressService;
    private final IamApi iamApi;

    @GetMapping
    public ResponseEntity<ApiResponse<List<DeliveryAddressResponse>>> getAddresses() {
        UUID userId = iamApi.getCurrentUserId();
        List<DeliveryAddressResponse> addresses = deliveryAddressService.getAddressesByUserId(userId);
        return ResponseEntity.ok(ApiResponse.ok(addresses));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> createAddress(
            @Valid @RequestBody CreateDeliveryAddressRequest request
    ) {
        UUID userId = iamApi.getCurrentUserId();
        DeliveryAddressResponse response = deliveryAddressService.createAddress(userId, request);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.ok(response, "Delivery address added successfully."));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> getAddressDetail(
            @PathVariable("id") UUID id
    ) {
        UUID userId = iamApi.getCurrentUserId();
        DeliveryAddressResponse response = deliveryAddressService.getAddressDetail(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> updateAddress(
            @PathVariable("id") UUID id,
            @Valid @RequestBody UpdateDeliveryAddressRequest request
    ) {
        UUID userId = iamApi.getCurrentUserId();
        DeliveryAddressResponse response = deliveryAddressService.updateAddress(userId, id, request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Address updated successfully."));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(
            @PathVariable("id") UUID id
    ) {
        UUID userId = iamApi.getCurrentUserId();
        deliveryAddressService.deleteAddress(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Address deleted successfully."));
    }

    @PatchMapping("/{id}/default")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> setDefaultAddress(
            @PathVariable("id") UUID id
    ) {
        UUID userId = iamApi.getCurrentUserId();
        DeliveryAddressResponse response = deliveryAddressService.setDefaultAddress(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(response, "Address updated successfully."));
    }
}
