package com.aquasuite.ims.security;

import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.UserStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

/**
 * UserDetails mang theo chính entity {@link User} (đã nạp sẵn roles/permissions) để bước
 * đăng nhập dùng lại, không phải truy vấn user lần thứ hai sau khi xác thực.
 */
public class AppUserDetails implements UserDetails {

    /** Người dùng đã nạp sẵn vai trò và quyền. */
    private final User user;

    /** Quyền hiện hành đã được chuẩn hóa. */
    private final Collection<GrantedAuthority> authorities;

    public AppUserDetails(User user, Collection<GrantedAuthority> authorities) {
        this.user = user;
        this.authorities = authorities;
    }

    /** Trả về entity người dùng để bước đăng nhập dùng lại, không truy vấn lần hai. */
    public User getUser() {
        return user;
    }

    /** Danh sách quyền hiện hành của người dùng. */
    @Override
    public Collection<GrantedAuthority> getAuthorities() {
        return authorities;
    }

    /** Mật khẩu đã băm dùng để so khớp BCrypt. */
    @Override
    public String getPassword() {
        return user.getPasswordHash();
    }

    /** Định danh đăng nhập là email, không phải username hiển thị của người dùng. */
    @Override
    public String getUsername() {
        return user.getEmail();
    }

    /** Tài khoản chỉ được xác thực khi ở trạng thái ACTIVE. */
    @Override
    public boolean isEnabled() {
        return user.getStatus() == UserStatus.ACTIVE;
    }
}
