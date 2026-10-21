package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.exception.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.payload.res.AvatarUploadResponse;
import com.aquasuite.ims.module.crm.internal.service.UserProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AvatarUploadControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private AvatarUploadController avatarUploadController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(avatarUploadController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        userId = UUID.randomUUID();
        lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    @DisplayName("POST /api/v1/profile/avatar: successful upload returns 200 OK and CDN URL")
    void uploadAvatar_Success_Returns200OKAndAvatarUrl() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "my_avatar.png", "image/png", new byte[]{1, 2, 3, 4}
        );
        String cdnUrl = "https://res.cloudinary.com/aquasuite/image/upload/v1/avatars/abc12345.png";

        when(userProfileService.uploadAvatar(eq(userId), any())).thenReturn(
                new AvatarUploadResponse(cdnUrl, "Avatar uploaded successfully.")
        );

        mockMvc.perform(multipart("/api/v1/profile/avatar").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Avatar uploaded successfully."))
                .andExpect(jsonPath("$.data.avatarUrl").value(cdnUrl));
    }

    @Test
    @DisplayName("POST /api/v1/profile/avatar: invalid file format or size > 5MB returns 400 Bad Request with MSG17")
    void uploadAvatar_InvalidFile_Returns400BadRequestWithMSG17() throws Exception {
        MockMultipartFile invalidFile = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", new byte[]{1, 2, 3}
        );

        when(userProfileService.uploadAvatar(eq(userId), any()))
                .thenThrow(new AppException(CrmErrorCode.INVALID_AVATAR));

        mockMvc.perform(multipart("/api/v1/profile/avatar").file(invalidFile))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("MSG17"))
                .andExpect(jsonPath("$.message").value("Avatar must be a JPEG, PNG, or WEBP image under 5MB."));
    }
}
