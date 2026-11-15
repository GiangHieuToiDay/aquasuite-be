package com.aquasuite.ims.module.servicedesk.internal.storage;

import lombok.Builder;

/**
 * Kết quả sau khi upload file media lên Cloudinary (hoặc mock CDN).
 */
@Builder
public record StoredMedia(
        String type,
        String url,
        String publicId,
        String contentType,
        long sizeBytes
) {
}

