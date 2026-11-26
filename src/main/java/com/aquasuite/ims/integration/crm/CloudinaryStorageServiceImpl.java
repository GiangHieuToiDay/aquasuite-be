package com.aquasuite.ims.integration.crm;

import com.aquasuite.ims.exception.crm.CrmErrorCode;
import com.aquasuite.ims.integration.crm.CloudinaryStorageService;
import com.aquasuite.ims.exception.AppException;
import com.cloudinary.Cloudinary;
import com.cloudinary.utils.ObjectUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class CloudinaryStorageServiceImpl implements CloudinaryStorageService {

    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    @Value("${cloudinary.cloud-name:}")
    private String cloudName;

    @Value("${cloudinary.api-key:}")
    private String apiKey;

    @Value("${cloudinary.api-secret:}")
    private String apiSecret;

    @Override
    public String uploadAvatar(MultipartFile file) {
        validateAvatarFile(file);

        if (isCloudinaryConfigured()) {
            return uploadToCloudinary(file);
        } else {
            return generateMockCdnUrl(file);
        }
    }

    private void validateAvatarFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            log.warn("Avatar upload rejected: file is null or empty");
            throw new AppException(CrmErrorCode.INVALID_AVATAR);
        }

        if (file.getSize() > MAX_FILE_SIZE) {
            log.warn("Avatar upload rejected: file size {} exceeds 5MB limit", file.getSize());
            throw new AppException(CrmErrorCode.AVATAR_TOO_LARGE);
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase())) {
            log.warn("Avatar upload rejected: unsupported content type {}", contentType);
            throw new AppException(CrmErrorCode.INVALID_AVATAR);
        }

        if (!hasMatchingImageExtension(file.getOriginalFilename(), contentType)) {
            log.warn("Avatar upload rejected: filename {} does not match content type {}", file.getOriginalFilename(), contentType);
            throw new AppException(CrmErrorCode.INVALID_AVATAR);
        }
    }

    static boolean hasMatchingImageExtension(String originalFilename, String contentType) {
        if (originalFilename == null || originalFilename.isBlank() || contentType == null) {
            return true;
        }
        String lower = originalFilename.trim().toLowerCase(java.util.Locale.ROOT);
        int dot = lower.lastIndexOf('.');
        if (dot < 0) {
            return true;
        }
        String ext = lower.substring(dot);
        String mime = contentType.toLowerCase(java.util.Locale.ROOT);
        return ((".jpg".equals(ext) || ".jpeg".equals(ext)) && ("image/jpeg".equals(mime) || "image/jpg".equals(mime)))
                || (".png".equals(ext) && "image/png".equals(mime))
                || (".webp".equals(ext) && "image/webp".equals(mime));
    }

    private boolean isCloudinaryConfigured() {
        return cloudName != null && !cloudName.isBlank()
                && apiKey != null && !apiKey.isBlank()
                && apiSecret != null && !apiSecret.isBlank();
    }

    private String uploadToCloudinary(MultipartFile file) {
        try {
            Cloudinary cloudinary = new Cloudinary(ObjectUtils.asMap(
                    "cloud_name", cloudName.trim(),
                    "api_key", apiKey.trim(),
                    "api_secret", apiSecret.trim(),
                    "secure", true
            ));

            Map<?, ?> uploadResult = cloudinary.uploader().upload(file.getBytes(), ObjectUtils.asMap(
                    "folder", "aquasuite/avatars",
                    "resource_type", "image"
            ));

            String secureUrl = (String) uploadResult.get("secure_url");
            log.info("Uploaded avatar successfully to Cloudinary: {}", secureUrl);
            return secureUrl;
        } catch (IOException e) {
            log.error("Failed to read avatar bytes or upload to Cloudinary", e);
            throw new AppException(CrmErrorCode.AVATAR_UPLOAD_FAILED);
        }
    }

    private String generateMockCdnUrl(MultipartFile file) {
        String filename = file.getOriginalFilename();
        String extension = "webp";
        if (filename != null && filename.contains(".")) {
            extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        }
        String mockId = UUID.randomUUID().toString().replace("-", "");
        String mockUrl = "https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/" + mockId + "." + extension;
        log.info("Cloudinary credentials not configured; generated mock CDN URL: {}", mockUrl);
        return mockUrl;
    }
}
