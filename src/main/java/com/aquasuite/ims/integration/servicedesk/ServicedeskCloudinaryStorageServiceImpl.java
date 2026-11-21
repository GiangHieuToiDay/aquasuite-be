package com.aquasuite.ims.integration.servicedesk;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.service.servicedesk.helper.MediaValidator;

import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.exception.AppException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ServicedeskCloudinaryStorageServiceImpl implements ServicedeskMediaStoragePort {

    private final MediaValidator mediaValidator;

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Override
    public StoredMedia uploadImage(MultipartFile file, UUID customerId) {
        mediaValidator.validateImage(file);
        return uploadInternal(file, customerId, "IMAGE", "image");
    }

    @Override
    public StoredMedia uploadVideo(MultipartFile file, UUID customerId) {
        mediaValidator.validateVideo(file);
        return uploadInternal(file, customerId, "VIDEO", "video");
    }

    private StoredMedia uploadInternal(MultipartFile file, UUID customerId, String mediaType, String resourceType) {
        if (isCloudinaryConfigured()) {
            return uploadToCloudinary(file, customerId, mediaType, resourceType);
        } else {
            return generateMockCdnMedia(file, customerId, mediaType);
        }
    }

    private boolean isCloudinaryConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    private StoredMedia uploadToCloudinary(MultipartFile file, UUID customerId, String mediaType, String resourceType) {
        try {
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim(),
                    "secure", true
            ));

            String folder = "aquasuite/warranty-tickets/" + customerId;
            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", folder,
                    "resource_type", resourceType
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            String publicId = (String) uploadResult.get("public_id");

            log.info("Uploaded {} successfully to Cloudinary: publicId={}, url={}", mediaType, publicId, secureUrl);
            return StoredMedia.builder()
                    .type(mediaType)
                    .url(secureUrl)
                    .publicId(publicId)
                    .contentType(file.getContentType())
                    .sizeBytes(file.getSize())
                    .build();
        } catch (IOException ex) {
            log.error("Failed to upload media to Cloudinary", ex);
            throw new AppException(ServicedeskErrorCode.MEDIA_UPLOAD_FAILED);
        }
    }

    private StoredMedia generateMockCdnMedia(MultipartFile file, UUID customerId, String mediaType) {
        String filename = file.getOriginalFilename();
        String extension = "IMAGE".equals(mediaType) ? "jpg" : "mp4";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        }
        UUID assetId = UUID.randomUUID();
        String publicId = "aquasuite/warranty-tickets/" + customerId + "/" + assetId;
        String mockUrl = "https://mock-cdn.aquasuite.local/" + publicId + "." + extension;

        log.info("Cloudinary unconfigured: generated mock CDN {} media: {}", mediaType, mockUrl);
        return StoredMedia.builder()
                .type(mediaType)
                .url(mockUrl)
                .publicId(publicId)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize())
                .build();
    }
}

