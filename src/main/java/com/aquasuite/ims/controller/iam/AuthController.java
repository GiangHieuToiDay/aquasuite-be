package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.dto.iam.request.*;
import com.aquasuite.ims.dto.iam.response.*;
import com.aquasuite.ims.service.iam.AuthService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.FieldErrorDetail;
import com.aquasuite.ims.exception.CommonErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    // ── Auth ─────────────────────────────────────────────────────────────────

    @PostMapping("/api/v1/auth/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.login(request), "Đăng nhập thành công"));
    }

    @PostMapping("/api/v1/auth/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(authService.refresh(request)));
    }

    @PostMapping("/api/v1/auth/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng xuất thành công"));
    }

    @GetMapping("/api/v1/auth/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse servletResponse) {
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        return ResponseEntity.ok(ApiResponse.ok(authService.getCurrentUser(jwt)));
    }

    // ── Register (from RegisterController) ───────────────────────────────────

    @PostMapping("/api/v1/iam/register")
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(authService.register(request),
                        "Please verify the code sent to your email."));
    }

    @PostMapping("/api/v1/iam/register/verifications")
    public ResponseEntity<ApiResponse<LoginResponse>> verify(@Valid @RequestBody VerifyRegistrationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(authService.verify(request),
                        "Account registered successfully! Welcome to AquaSuite."));
    }

    @PostMapping("/api/v1/iam/register/otp-resends")
    public ResponseEntity<ApiResponse<Void>> resend(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        authService.resend(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(null, "A verification code has been sent to your email."));
    }

    // ── Password (from PasswordController) ───────────────────────────────────

    @PostMapping("/api/v1/iam/password/change")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request) {
        PasswordActionResponse response = authService.changePassword(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/api/v1/iam/password/forgot")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request) {
        PasswordActionResponse response = authService.requestPasswordRecovery(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @PostMapping("/api/v1/iam/password/reset")
    public ResponseEntity<ApiResponse<PasswordActionResponse>> resetPassword(
            @Valid @RequestBody ResetPasswordWithOtpRequest request) {
        PasswordActionResponse response = authService.resetPasswordWithOtp(request);
        return ResponseEntity.ok(ApiResponse.ok(response, response.message()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error) {
        var fields =
                error.getBindingResult().getFieldErrors().stream()
                        .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.VALIDATION_ERROR.getCode(),
                                CommonErrorCode.VALIDATION_ERROR.getMessage(),
                                fields));
    }
}
