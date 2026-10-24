package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.request.LoginRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.RefreshRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.CurrentUserResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.TokenResponse;
import com.aquasuite.ims.module.iam.internal.service.AuthService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đăng nhập thành công"));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng xuất thành công"));
    }

    /**
     * Returns the identity and current active roles of the authenticated access JWT subject.
     * {@code Cache-Control: no-store} is set before resolution so it also applies to error
     * responses produced by the exception handler for this endpoint.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse servletResponse) {
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        CurrentUserResponse response = authService.getCurrentUser(jwt);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
