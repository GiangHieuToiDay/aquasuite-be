package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.share.exception.BaseErrorCode;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.convert.converter.Converter;
import org.springframework.dao.DataAccessException;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class AccessAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final CurrentAuthoritiesService currentAuthoritiesService;

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        UUID userId;
        try {
            userId = UUID.fromString(jwt.getSubject());
        } catch (Exception e) {
            log.warn("Failed to parse UUID subject from access token: {}", e.getMessage());
            setAuthError(CommonErrorCode.UNAUTHORIZED);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("invalid_token", "Invalid sub claim", null));
        }

        // 1. Load current user and status/authorities from PostgreSQL
        User user;
        try {
            Optional<User> userOpt = currentAuthoritiesService.loadCurrentUser(userId);
            if (userOpt.isEmpty()) {
                log.warn("User {} not found in PostgreSQL", userId);
                setAuthError(CommonErrorCode.UNAUTHORIZED);
                throw new OAuth2AuthenticationException(
                        new OAuth2Error("invalid_token", "User not found", null));
            }
            user = userOpt.get();
        } catch (DataAccessException e) {
            log.error("PostgreSQL failure during user lookup for userId: {}", userId, e);
            setAuthError(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("service_unavailable", "Database unavailable", null));
        } catch (OAuth2AuthenticationException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error looking up user {}", userId, e);
            setAuthError(AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("service_unavailable", "Database error", null));
        }

        // 2. Check ACTIVE status
        if (user.getStatus() != UserStatus.ACTIVE) {
            log.info("User {} account status is {} (not ACTIVE)", userId, user.getStatus());
            setAuthError(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
            throw new OAuth2AuthenticationException(
                    new OAuth2Error("account_not_active", "Account not active", null));
        }

        // 3. Resolve current granted authorities
        Collection<GrantedAuthority> authorities =
                currentAuthoritiesService.resolveAuthorities(user.getRoles());

        return new JwtAuthenticationToken(jwt, authorities, user.getUsername());
    }

    private void setAuthError(BaseErrorCode errorCode) {
        RequestAttributes attr = RequestContextHolder.getRequestAttributes();
        if (attr instanceof ServletRequestAttributes servletRequestAttributes) {
            servletRequestAttributes
                    .getRequest()
                    .setAttribute(AuthSecurityErrorHandlers.AUTH_ERROR_ATTRIBUTE, errorCode);
        }
    }
}
