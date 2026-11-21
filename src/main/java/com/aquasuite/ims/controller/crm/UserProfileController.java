package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.crm.response.UserProfileResponse;
import com.aquasuite.ims.service.crm.UserProfileService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profile")
@RequiredArgsConstructor
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final IamQueryService iamApi;

    @GetMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> getProfile() {
        UUID userId = iamApi.getCurrentUserId();
        UserProfileResponse profile = userProfileService.getProfile(userId);
        return ResponseEntity.ok(ApiResponse.ok(profile));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserProfileResponse>> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        UUID userId = iamApi.getCurrentUserId();
        UserProfileResponse updatedProfile = userProfileService.updateProfile(userId, request);
        return ResponseEntity.ok(ApiResponse.ok(updatedProfile, "Profile updated successfully."));
    }
}
