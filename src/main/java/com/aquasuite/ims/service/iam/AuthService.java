package com.aquasuite.ims.service.iam;

import com.aquasuite.ims.dto.iam.request.LoginRequest;
import com.aquasuite.ims.dto.iam.request.RefreshRequest;
import com.aquasuite.ims.dto.iam.response.CurrentUserResponse;
import com.aquasuite.ims.dto.iam.response.LoginResponse;
import com.aquasuite.ims.dto.iam.response.TokenResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface AuthService {

    /**
     * Kiểm tra email/mật khẩu và trạng thái tài khoản, sau đó tạo phiên Redis
     * cùng access token và refresh token cho người dùng hợp lệ.
     */
    LoginResponse login(LoginRequest request);

    /**
     * Tạo phiên đăng nhập cho tài khoản vừa kích hoạt sau khi giao dịch đăng ký
     * đã hoàn tất; không xác thực lại mật khẩu.
     */
    LoginResponse loginRegisteredUser(java.util.UUID userId);

    /**
     * Xác minh refresh token, kiểm tra phiên hiện hành, chống tái sử dụng token
     * và cấp cặp token kế tiếp bằng cơ chế xoay vòng nguyên tử.
     */
    TokenResponse refresh(RefreshRequest request);

    /** Thu hồi phiên Redis được xác định từ subject và session ID trong access JWT. */
    void logout(Jwt jwt);

    /**
     * Đọc lại hồ sơ và vai trò đang hoạt động của chủ thể access JWT từ database
     * để phản ánh thay đổi quyền ngay ở request kế tiếp.
     */
    CurrentUserResponse getCurrentUser(Jwt jwt);
}
