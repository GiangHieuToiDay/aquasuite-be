package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.*;
import com.aquasuite.ims.dto.iam.response.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Service xác thực và ủy quyền người dùng hợp nhất (gộp đăng nhập, đăng ký OTP và đổi/quên mật khẩu).
 */
public interface AuthService {

    LoginResponse login(LoginRequest request);

    LoginResponse loginRegisteredUser(UUID userId);

    TokenResponse refresh(RefreshRequest request);

    void logout(Jwt jwt);

    CurrentUserResponse getCurrentUser(Jwt jwt);

    RegisterResponse register(RegisterRequest request);

    void resend(ResendRegistrationOtpRequest request);

    LoginResponse verify(VerifyRegistrationRequest request);

    PasswordActionResponse changePassword(ChangePasswordRequest req);

    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
