package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.exception.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.service.impl.CloudinaryStorageServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CloudinaryStorageServiceTest {

    private CloudinaryStorageServiceImpl storageService;

    @BeforeEach
    void setUp() {
        storageService = new CloudinaryStorageServiceImpl();
    }

    @Test
    @DisplayName("Upload avatar with null or empty file throws MSG17")
    void uploadAvatar_NullOrEmptyFile_ThrowsMSG17() {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.png", "image/png", new byte[0]
        );

        assertThatThrownBy(() -> storageService.uploadAvatar(null))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode().getCode()).isEqualTo("MSG17");
                });

        assertThatThrownBy(() -> storageService.uploadAvatar(emptyFile))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode().getCode()).isEqualTo("MSG17");
                });
    }

    @Test
    @DisplayName("Upload avatar exceeding 5MB throws MSG17")
    void uploadAvatar_FileExceeds5MB_ThrowsMSG17() {
        byte[] largeBytes = new byte[5 * 1024 * 1024 + 1]; // 5MB + 1 byte
        MockMultipartFile largeFile = new MockMultipartFile(
                "file", "large.png", "image/png", largeBytes
        );

        assertThatThrownBy(() -> storageService.uploadAvatar(largeFile))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode().getCode()).isEqualTo("MSG17");
                });
    }

    @Test
    @DisplayName("Upload avatar with invalid MIME type throws MSG17")
    void uploadAvatar_InvalidMimeType_ThrowsMSG17() {
        MockMultipartFile textFile = new MockMultipartFile(
                "file", "doc.pdf", "application/pdf", new byte[]{1, 2, 3}
        );

        assertThatThrownBy(() -> storageService.uploadAvatar(textFile))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> {
                    AppException appEx = (AppException) ex;
                    assertThat(appEx.getErrorCode().getCode()).isEqualTo("MSG17");
                });
    }

    @Test
    @DisplayName("Upload valid avatar returns deterministic mock CDN URL when unconfigured")
    void uploadAvatar_ValidAvatar_ReturnsMockCdnUrl() {
        MockMultipartFile validImage = new MockMultipartFile(
                "file", "avatar.jpg", "image/jpeg", new byte[]{1, 2, 3, 4}
        );

        String cdnUrl = storageService.uploadAvatar(validImage);

        assertThat(cdnUrl).isNotNull();
        assertThat(cdnUrl).startsWith("https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/");
        assertThat(cdnUrl).endsWith(".jpg");
    }
}
