package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.dto.crm.request.CreateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.crm.response.AvatarUploadResponse;
import com.aquasuite.ims.dto.crm.response.DeliveryAddressResponse;
import com.aquasuite.ims.dto.crm.response.UserProfileResponse;
import com.aquasuite.ims.service.crm.UserProfileService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final IamQueryService iamApi;

    @Autowired
    public UserProfileController(UserProfileService userProfileService, IamQueryService iamApi) {
        this.userProfileService = userProfileService;
        this.iamApi = iamApi;
    }

    // ── Profile ──────────────────────────────────────────────────────────────

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getProfile(userId)));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                userProfileService.updateProfile(userId, request), "Profile updated successfully."));
    }

    // ── Avatar (from AvatarUploadController) ─────────────────────────────────

    @PostMapping(value = "/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                userProfileService.uploadAvatar(userId, file), "Avatar uploaded successfully."));
    }

    // ── Delivery Addresses (from DeliveryAddressController) ───────────────────

    @GetMapping("/addresses")
    public ResponseEntity<ApiResponse<List<DeliveryAddressResponse>>> getAddresses() {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getAddressesByUserId(userId)));
    }

    @PostMapping("/addresses")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> createAddress(
            @Valid @RequestBody CreateDeliveryAddressRequest request) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(
                userProfileService.createAddress(userId, request), "Delivery address added successfully."));
    }

    @GetMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> getAddressDetail(@PathVariable UUID id) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(userProfileService.getAddressDetail(userId, id)));
    }

    @PutMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> updateAddress(
            @PathVariable UUID id, @Valid @RequestBody UpdateDeliveryAddressRequest request) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                userProfileService.updateAddress(userId, id, request), "Address updated successfully."));
    }

    @DeleteMapping("/addresses/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteAddress(@PathVariable UUID id) {
        UUID userId = iamApi.getCurrentUserId();
        userProfileService.deleteAddress(userId, id);
        return ResponseEntity.ok(ApiResponse.ok(null, "Address deleted successfully."));
    }

    @PatchMapping("/addresses/{id}/default")
    public ResponseEntity<ApiResponse<DeliveryAddressResponse>> setDefaultAddress(@PathVariable UUID id) {
        UUID userId = iamApi.getCurrentUserId();
        return ResponseEntity.ok(ApiResponse.ok(
                userProfileService.setDefaultAddress(userId, id), "Address updated successfully."));
    }
}
