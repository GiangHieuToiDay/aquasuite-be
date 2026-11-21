package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.crm.response.AvatarUploadResponse;
import com.aquasuite.ims.dto.crm.response.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file);
}
