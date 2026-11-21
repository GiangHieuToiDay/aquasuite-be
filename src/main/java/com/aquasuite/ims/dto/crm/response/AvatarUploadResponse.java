package com.aquasuite.ims.dto.crm.response;

import lombok.Builder;

@Builder
public record AvatarUploadResponse(
        String avatarUrl,
        String message
) {
    public AvatarUploadResponse(String avatarUrl) {
        this(avatarUrl, "Avatar uploaded successfully.");
    }
}
