package com.aquasuite.ims.controller.iam;


import com.aquasuite.ims.dto.iam.request.ChangePasswordRequest;
import com.aquasuite.ims.dto.iam.request.ForgotPasswordRequest;
import com.aquasuite.ims.dto.iam.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.dto.iam.response.PasswordActionResponse;
import com.aquasuite.ims.service.iam.PasswordService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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