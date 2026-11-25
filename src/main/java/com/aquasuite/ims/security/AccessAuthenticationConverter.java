package com.aquasuite.ims.security;

import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.service.iam.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.exception.BaseErrorCode;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.UUID;

/**
 * Chuyển access JWT đã được giải mã thành {@link AbstractAuthenticationToken} dùng cho phân quyền.
 * Mỗi request đều đọc lại người dùng, trạng thái và vai trò/quyền từ PostgreSQL nên thay đổi quyền
 * có hiệu lực ngay, không phải chờ token hết hạn.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AccessAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final CurrentAuthoritiesService currentAuthoritiesService;

    /**
     * Đọc user ID từ JWT, kiểm tra tài khoản còn ACTIVE, tải vai trò/quyền mới
     * nhất từ database và tạo Authentication dùng cho kiểm tra phân quyền.
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId = parseUserId(jwt);

        // 1. Tải người dùng cùng vai trò và quyền hiện hành từ PostgreSQL
        User user;
        try {
            user = currentAuthoritiesService.loadCurrentUser(userId).orElse(null);
        } catch (RuntimeException e) {
            log.error("PostgreSQL failure during user lookup for userId: {}", userId, e);
            throw reject(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE, "service_unavailable", "Database unavailable");
        }
        if (user == null) {
            log.warn("User {} not found in PostgreSQL", userId);
            throw reject(CommonErrorCode.UNAUTHORIZED, "invalid_token", "User not found");
        }
        // 2. Chỉ tài khoản đang ACTIVE mới được đi tiếp
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} account status is {} (not ACTIVE)", userId, user.getStatus());
            throw reject(AuthErrorCode.ACCOUNT_NOT_ACTIVE, "account_not_active", "Account not active");
        }

        // 3. Tính danh sách quyền hiện hành rồi tạo Authentication
        Collection<GrantedAuthority> authorities =
                currentAuthoritiesService.resolveAuthorities(user.getRoles());
        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }

    /** Đọc user ID từ claim sub của JWT; sai định dạng UUID thì từ chối token. */
    private UUID parseUserId(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            log.warn("Failed to parse UUID subject from access token: {}", e.getMessage());
            throw reject(CommonErrorCode.UNAUTHORIZED, "invalid_token", "Invalid sub claim");
        }
    }

    /** Gắn mã lỗi vào request để entry point trả response thống nhất, rồi tạo exception để ném. */
    private OAuth2AuthenticationException reject(
            BaseErrorCode errorCode, String oauthErrorCode, String description) {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            attrs.getRequest().setAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE, errorCode);
        }
        return new OAuth2AuthenticationException(new OAuth2Error(oauthErrorCode, description, null));
    }
}
