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

    /**
     * Nhận email/mật khẩu, xác thực tài khoản và trả về thông tin người dùng
     * cùng access token, refresh token của phiên mới.
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<LoginResponse>> login(
            @Valid @RequestBody LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.ok(response, "Đăng nhập thành công"));
    }

    /**
     * Nhận refresh token hiện tại và trả về cặp token kế tiếp; token cũ không
     * còn được phép sử dụng lại sau khi xoay vòng thành công.
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(
            @Valid @RequestBody RefreshRequest request) {
        TokenResponse response = authService.refresh(request);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * Thu hồi toàn bộ phiên được chỉ định trong access JWT hiện tại, khiến các
     * token còn lại của chính phiên đó không thể tiếp tục truy cập hệ thống.
     */
    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<Void>> logout(@AuthenticationPrincipal Jwt jwt) {
        authService.logout(jwt);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đăng xuất thành công"));
    }

    /**
     * Trả về hồ sơ và vai trò đang hoạt động của người dùng đã xác thực. Dữ liệu
     * được đọc lại từ database và response bị cấm cache để tránh lộ thông tin IAM.
     */
    @GetMapping("/me")
    public ResponseEntity<ApiResponse<CurrentUserResponse>> me(
            @AuthenticationPrincipal Jwt jwt, HttpServletResponse servletResponse) {
        servletResponse.setHeader(HttpHeaders.CACHE_CONTROL, CacheControl.noStore().getHeaderValue());
        CurrentUserResponse response = authService.getCurrentUser(jwt);
        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
