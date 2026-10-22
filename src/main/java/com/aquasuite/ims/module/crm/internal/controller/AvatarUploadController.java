package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.payload.res.AvatarUploadResponse;
import com.aquasuite.ims.module.crm.internal.service.UserProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/profile/avatar")
@RequiredArgsConstructor
public class AvatarUploadController {

    private final UserProfileService userProfileService;
    private final IamApi iamApi;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = iamApi.getCurrentUserId();
        AvatarUploadResponse response = userProfileService.uploadAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.ok(response, "Avatar uploaded successfully."));
    }
}
