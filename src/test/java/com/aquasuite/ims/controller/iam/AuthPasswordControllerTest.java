package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.dto.iam.request.ChangePasswordRequest;
import com.aquasuite.ims.dto.iam.request.ForgotPasswordRequest;
import com.aquasuite.ims.dto.iam.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.dto.iam.response.PasswordActionResponse;
import com.aquasuite.ims.enums.iam.RecoveryChannel;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.exception.iam.IamPasswordErrorCode;
import com.aquasuite.ims.service.iam.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller & validation tests cho UC-02.4 (Change Password) và UC-02.5 (Forgot/Reset Password).
 */
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.security\\..*"))
@Import({GlobalExceptionHandler.class, AuthPasswordControllerTest.TestSecurityConfig.class})
class AuthPasswordControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @MockitoBean private AuthService authService;

    @Test
    void changePassword_unauthenticated_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/iam/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPwd@123", "NewPwd@456", "NewPwd@456"))))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(authService);
    }

    @Test
    @WithMockUser(roles = "CUSTOMER")
    void changePassword_authenticatedValid_returns200() throws Exception {
        when(authService.changePassword(any(ChangePasswordRequest.class)))
                .thenReturn(new PasswordActionResponse("Password changed successfully. Please log in again if required."));

        mockMvc.perform(post("/api/v1/iam/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ChangePasswordRequest("OldPwd@123", "NewPwd@456", "NewPwd@456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Password changed successfully. Please log in again if required."));
        verify(authService).changePassword(any(ChangePasswordRequest.class));
    }

    @Test
    @WithMockUser(roles = "SALES_STAFF")
    void changePassword_invalidPayload_returns400ValidationError() throws Exception {
        mockMvc.perform(post("/api/v1/iam/password/change")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"currentPassword":"","newPassword":"short","confirmNewPassword":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));
        verifyNoInteractions(authService);
    }

    @Test
    @WithMockUser
    void forgotPassword_validEmailAndRateLimit_mapsStatusAndError() throws Exception {
        when(authService.requestPasswordRecovery(any(ForgotPasswordRequest.class)))
                .thenReturn(new PasswordActionResponse("A password reset code has been sent to your email."));

        mockMvc.perform(post("/api/v1/iam/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("user@aquasuite.vn", RecoveryChannel.EMAIL))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        when(authService.requestPasswordRecovery(any(ForgotPasswordRequest.class)))
                .thenThrow(new AppException(IamPasswordErrorCode.EMAIL_RATE_LIMIT_EXCEEDED));

        mockMvc.perform(post("/api/v1/iam/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ForgotPasswordRequest("user@aquasuite.vn", RecoveryChannel.EMAIL))))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("IAM_PWD_006"));
    }

    @Test
    @WithMockUser
    void forgotAndResetPassword_validationAndChannelErrors() throws Exception {
        mockMvc.perform(post("/api/v1/iam/password/forgot")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":" ","channel":null}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        mockMvc.perform(post("/api/v1/iam/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"identifier":"","otpCode":"","newPassword":"123","confirmNewPassword":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SYS_003"));

        when(authService.resetPasswordWithOtp(any(ResetPasswordWithOtpRequest.class)))
                .thenReturn(new PasswordActionResponse("Your password has been reset successfully. You may now sign in."));

        mockMvc.perform(post("/api/v1/iam/password/reset")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                new ResetPasswordWithOtpRequest("user@aquasuite.vn", "123456", "NewPwd@456", "NewPwd@456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @TestConfiguration
    @EnableMethodSecurity
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http
                    .csrf(AbstractHttpConfigurer::disable)
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .httpBasic(httpBasic -> { })
                    .build();
        }
    }
}
