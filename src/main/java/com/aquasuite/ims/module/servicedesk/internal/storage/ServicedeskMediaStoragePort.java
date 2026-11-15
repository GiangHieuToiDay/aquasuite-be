package com.aquasuite.ims.module.servicedesk.internal.storage;

import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Cổng trừu tượng lưu trữ media (ảnh/video) của module Servicedesk (UC-47).
 */
public interface ServicedeskMediaStoragePort {

    StoredMedia uploadImage(MultipartFile file, UUID customerId);

    StoredMedia uploadVideo(MultipartFile file, UUID customerId);
}

