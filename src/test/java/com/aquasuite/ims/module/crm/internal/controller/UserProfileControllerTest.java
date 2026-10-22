package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.UserProfileResponse;
import com.aquasuite.ims.module.crm.internal.service.UserProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class UserProfileControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private UserProfileService userProfileService;

    @Mock
    private IamApi iamApi;

    @InjectMocks
    private UserProfileController userProfileController;

    private UUID userId;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userProfileController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        userId = UUID.randomUUID();
        org.mockito.Mockito.lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
    }

    @Test
    @DisplayName("GET /api/v1/profile: returns 200 OK with profile details")
    void getProfile_Returns200OK() throws Exception {
        UserProfileResponse response = UserProfileResponse.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van Test")
                .email("test@example.com")
                .phone("0912345678")
                .customerCode("KH-001")
                .customerTier("RETAIL")
                .roles(List.of("CUSTOMER"))
                .deliveryAddresses(List.of())
                .build();

        when(userProfileService.getProfile(userId)).thenReturn(response);

        mockMvc.perform(get("/api/v1/profile"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Test"))
                .andExpect(jsonPath("$.data.customerCode").value("KH-001"))
                .andExpect(jsonPath("$.data.customerTier").value("RETAIL"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile: updates profile and returns MSG13 message")
    void updateProfile_Success_ReturnsMSG13() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Nguyen Van Updated")
                .phone("0987654321")
                .avatarUrl("https://res.cloudinary.com/avatar.png")
                .build();

        UserProfileResponse response = UserProfileResponse.builder()
                .id(userId)
                .username("testuser")
                .fullName("Nguyen Van Updated")
                .email("test@example.com")
                .phone("0987654321")
                .avatarUrl("https://res.cloudinary.com/avatar.png")
                .customerCode("KH-001")
                .customerTier("RETAIL")
                .roles(List.of("CUSTOMER"))
                .deliveryAddresses(List.of())
                .build();

        when(userProfileService.updateProfile(eq(userId), any(UpdateProfileRequest.class)))
                .thenReturn(response);

        mockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Profile updated successfully."))
                .andExpect(jsonPath("$.data.fullName").value("Nguyen Van Updated"))
                .andExpect(jsonPath("$.data.phone").value("0987654321"));
    }

    @Test
    @DisplayName("PUT /api/v1/profile: invalid phone format rejected with 400 validation error")
    void updateProfile_InvalidPhone_Returns400ValidationError() throws Exception {
        UpdateProfileRequest request = UpdateProfileRequest.builder()
                .fullName("Nguyen Van Test")
                .phone("123456") // Invalid format, must start with 0 and have 10 digits
                .build();

        mockMvc.perform(put("/api/v1/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.code").value("SYS_003"));
    }
}
