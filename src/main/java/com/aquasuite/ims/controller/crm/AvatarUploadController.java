package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.dto.crm.response.AvatarUploadResponse;
import com.aquasuite.ims.service.crm.UserProfileService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.common.ApiResponse;
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
    private final IamQueryService iamApi;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<AvatarUploadResponse>> uploadAvatar(
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = iamApi.getCurrentUserId();
        AvatarUploadResponse response = userProfileService.uploadAvatar(userId, file);
        return ResponseEntity.ok(ApiResponse.ok(response, "Avatar uploaded successfully."));
    }
}
