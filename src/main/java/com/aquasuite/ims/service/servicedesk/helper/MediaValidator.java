package com.aquasuite.ims.service.servicedesk.helper;

import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.exception.AppException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Validator kiểm tra tính hợp lệ của file ảnh/video và danh sách media khi gửi ticket (UC-47).
 */
@Component
public class MediaValidator {

    public static final long MAX_IMAGE_SIZE = 5L * 1024 * 1024;  // 5 MB
    public static final long MAX_VIDEO_SIZE = 20L * 1024 * 1024; // 20 MB

    public static final int MAX_IMAGE_COUNT = 5;
    public static final int MAX_VIDEO_COUNT = 1;

    private static final Set<String> ALLOWED_IMAGE_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/webp"
    );

    private static final Set<String> ALLOWED_IMAGE_EXTS = Set.of(
            "jpg", "jpeg", "png", "webp"
    );

    private static final Set<String> ALLOWED_VIDEO_TYPES = Set.of(
            "video/mp4",
            "video/quicktime"
    );

    private static final Set<String> ALLOWED_VIDEO_EXTS = Set.of(
            "mp4", "mov"
    );

    public void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "File rỗng hoặc không tồn tại.");
        }
        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Dung lượng ảnh vượt quá giới hạn 5MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Định dạng ảnh không được hỗ trợ (chỉ nhận JPEG, PNG, WEBP).");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_IMAGE_EXTS.contains(ext)) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Đuôi file ảnh không hợp lệ (chỉ nhận .jpg, .jpeg, .png, .webp).");
        }
    }

    public void validateVideo(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "File rỗng hoặc không tồn tại.");
        }
        if (file.getSize() > MAX_VIDEO_SIZE) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Dung lượng video vượt quá giới hạn 20MB.");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_VIDEO_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Định dạng video không được hỗ trợ (chỉ nhận MP4, MOV).");
        }
        String ext = extractExtension(file.getOriginalFilename());
        if (ext == null || !ALLOWED_VIDEO_EXTS.contains(ext)) {
            throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Đuôi file video không hợp lệ (chỉ nhận .mp4, .mov).");
        }
    }

    public void validateMediaList(List<TicketMedia> mediaList, UUID ownerCustomerId) {
        if (mediaList == null || mediaList.isEmpty()) {
            return;
        }

        long imageCount = mediaList.stream()
                .filter(m -> "IMAGE".equalsIgnoreCase(m.type()))
                .count();
        long videoCount = mediaList.stream()
                .filter(m -> "VIDEO".equalsIgnoreCase(m.type()))
                .count();

        if (imageCount > MAX_IMAGE_COUNT || videoCount > MAX_VIDEO_COUNT) {
            throw new AppException(ServicedeskErrorCode.MEDIA_COUNT_LIMIT_EXCEEDED);
        }

        String expectedFolderSegment = ownerCustomerId != null ? ownerCustomerId.toString() : "";
        for (TicketMedia media : mediaList) {
            if (!StringUtils.hasText(media.url()) || !StringUtils.hasText(media.publicId())) {
                throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "Thiếu URL hoặc Public ID của file đính kèm.");
            }
            if (StringUtils.hasText(expectedFolderSegment) && !media.url().contains(expectedFolderSegment) && !media.publicId().contains(expectedFolderSegment)) {
                throw new AppException(ServicedeskErrorCode.INVALID_MEDIA_FILE, "URL media không thuộc thư mục quản lý của khách hàng.");
            }
        }
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return null;
        }
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    }
}

