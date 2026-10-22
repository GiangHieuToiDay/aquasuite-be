package com.aquasuite.ims.module.crm.internal.payload.res;

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
