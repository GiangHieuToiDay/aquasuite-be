package com.aquasuite.ims.module.crm.internal.service;

import org.springframework.web.multipart.MultipartFile;

public interface CloudinaryStorageService {

    /**
     * Kiểm tra và tải lên file ảnh đại diện lên Cloudinary:
     * - Kích thước <= 5MB
     * - Định dạng hợp lệ: JPEG, PNG, WEBP
     * Vi phạm sẽ ném AppException(CrmErrorCode.INVALID_AVATAR) kèm mã MSG17.
     */
    String uploadAvatar(MultipartFile file);
}
