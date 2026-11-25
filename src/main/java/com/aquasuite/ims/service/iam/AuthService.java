package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.*;
import com.aquasuite.ims.dto.iam.response.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.UUID;

/**
 * Service xác thực và ủy quyền người dùng hợp nhất (gộp đăng nhập, đăng ký OTP và đổi/quên mật khẩu).
 */
public interface AuthService {

    /** Đăng nhập bằng email và mật khẩu, tạo phiên mới và phát hành cặp token. */
    LoginResponse login(LoginRequest request);

    /** Tạo phiên cho tài khoản vừa kích hoạt mà không cần nhập lại mật khẩu. */
    LoginResponse loginRegisteredUser(UUID userId);

    /** Xoay vòng refresh token: kiểm tra token, tài khoản và phiên rồi phát hành cặp token mới. */
    TokenResponse refresh(RefreshRequest request);

    /** Thu hồi phiên gắn với access token của người dùng. */
    void logout(Jwt jwt);

    /** Lấy hồ sơ và vai trò hiện hành của người dùng từ cơ sở dữ liệu. */
    CurrentUserResponse getCurrentUser(Jwt jwt);

    /** Bước 1 đăng ký: kiểm tra trùng, lưu thông tin chờ kích hoạt và gửi OTP. */
    RegisterResponse register(RegisterRequest request);

    /** Gửi lại mã OTP đăng ký. */
    void resend(ResendRegistrationOtpRequest request);

    /** Bước 2 đăng ký: xác minh OTP, tạo tài khoản khách hàng và đăng nhập luôn. */
    LoginResponse verify(VerifyRegistrationRequest request);

    /** Đổi mật khẩu của người dùng đang đăng nhập. */
    PasswordActionResponse changePassword(ChangePasswordRequest req);

    /** Tạo và gửi mã OTP khôi phục mật khẩu qua email hoặc số điện thoại. */
    PasswordActionResponse requestPasswordRecovery(ForgotPasswordRequest req);

    /** Đặt lại mật khẩu sau khi kiểm tra mã OTP khôi phục. */
    PasswordActionResponse resetPasswordWithOtp(ResetPasswordWithOtpRequest req);
}
