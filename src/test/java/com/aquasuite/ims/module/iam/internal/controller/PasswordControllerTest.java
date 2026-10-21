package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.entity.enums.RecoveryChannel;
import com.aquasuite.ims.module.iam.internal.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.service.PasswordService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class PasswordControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private PasswordService passwordService;

    @InjectMocks private PasswordController passwordController;

    @BeforeEach
    void setUp() {
        mockMvc =
                MockMvcBuilders.standaloneSetup(passwordController)
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
    }

    @Test
    @DisplayName("POST /api/v1/iam/password/change returns HTTP 200 and MSG18")
    void testChangePassword() throws Exception {
        when(passwordService.changePassword(any(ChangePasswordRequest.class)))
                .thenReturn(
                        new PasswordActionResponse(
                                "Password changed successfully. Please log in again if required."));

        ChangePasswordRequest request =
                new ChangePasswordRequest("OldPassword@123", "NewPassword@456", "NewPassword@456");

        mockMvc.perform(
                        post("/api/v1/iam/password/change")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Password changed successfully. Please log in again if required."))
                .andExpect(
                        jsonPath("$.data.message")
                                .value(
                                        "Password changed successfully. Please log in again if required."));
    }

    @Test
    @DisplayName("POST /api/v1/iam/password/forgot returns HTTP 200 and MSG19")
    void testForgotPassword() throws Exception {
        when(passwordService.requestPasswordRecovery(any(ForgotPasswordRequest.class)))
                .thenReturn(
                        new PasswordActionResponse(
                                "A password reset code has been sent to your email."));

        ForgotPasswordRequest request =
                new ForgotPasswordRequest("user@example.com", RecoveryChannel.EMAIL);

        mockMvc.perform(
                        post("/api/v1/iam/password/forgot")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(
                        jsonPath("$.message")
                                .value("A password reset code has been sent to your email."));
    }

    @Test
    @DisplayName("POST /api/v1/iam/password/reset returns HTTP 200 and MSG20")
    void testResetPassword() throws Exception {
        when(passwordService.resetPasswordWithOtp(any(ResetPasswordWithOtpRequest.class)))
                .thenReturn(
                        new PasswordActionResponse(
                                "Your password has been reset successfully. You may now sign in."));

        ResetPasswordWithOtpRequest request =
                new ResetPasswordWithOtpRequest(
                        "user@example.com", "123456", "NewPassword@456", "NewPassword@456");

        mockMvc.perform(
                        post("/api/v1/iam/password/reset")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.code").value("SUCCESS"))
                .andExpect(
                        jsonPath("$.message")
                                .value(
                                        "Your password has been reset successfully. You may now sign in."));
    }
}
