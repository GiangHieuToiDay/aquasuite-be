package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.AvatarUploadResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.UserProfileResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

public interface UserProfileService {

    UserProfileResponse getProfile(UUID userId);

    UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request);

    AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file);
}
