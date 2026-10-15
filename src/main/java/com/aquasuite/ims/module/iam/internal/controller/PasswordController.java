package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.module.iam.internal.service.PasswordService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/iam/password")
@RequiredArgsConstructor
public class PasswordController {

    private final PasswordService passwordService;

    @PostMapping("/change")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        PasswordActionResponse response = passwordService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/forgot")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        PasswordActionResponse response = passwordService.requestPasswordRecovery(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordWithOtpRequest request
    ) {
        PasswordActionResponse response = passwordService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }
}
