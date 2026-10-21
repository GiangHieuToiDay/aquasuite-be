package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.service.PasswordRecoveryService;
import com.aquasuite.ims.module.iam.internal.payload.request.ChangePasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.ForgotPasswordRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PasswordActionResponse;
import com.aquasuite.ims.module.iam.internal.payload.request.ResetPasswordWithOtpRequest;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam/password")
@RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService service;
    @PostMapping("/change") public ResponseEntity<ApiResponse<PasswordActionResponse>> change(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody ChangePasswordRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(service.changePassword(UUID.fromString(jwt.getSubject()), request)));
    }
    @PostMapping("/forgot") public ResponseEntity<ApiResponse<PasswordActionResponse>> forgot(@Valid @RequestBody ForgotPasswordRequest request) { return ResponseEntity.ok(ApiResponse.ok(service.requestRecovery(request))); }
    @PostMapping("/reset") public ResponseEntity<ApiResponse<PasswordActionResponse>> reset(@Valid @RequestBody ResetPasswordWithOtpRequest request) { return ResponseEntity.ok(ApiResponse.ok(service.resetPassword(request))); }
}
