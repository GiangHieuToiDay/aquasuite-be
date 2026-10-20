package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.BaseErrorCode;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSecurityErrorHandlers {

    public static final String AUTH_ERROR_ATTRIBUTE = "AQUASUITE_AUTH_ERROR";

    private final ObjectMapper objectMapper;

    public AuthenticationEntryPoint authenticationEntryPoint() {
        return this::commence;
    }

    public AccessDeniedHandler accessDeniedHandler() {
        return this::handleAccessDenied;
    }

    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException authException)
            throws IOException {
        log.warn("Authentication failed for request [{} {}]: {}", request.getMethod(), request.getRequestURI(), authException.getMessage());

        // Check if an explicit error was set in request attribute by converter or filter
        Object errorAttr = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        if (errorAttr instanceof BaseErrorCode baseErrorCode) {
            writeResponse(response, baseErrorCode.getStatus().value(), baseErrorCode.getCode(), baseErrorCode.getMessage());
            return;
        }

        // Check if the authException has an underlying AppException cause
        Throwable cause = authException.getCause();
        while (cause != null) {
            if (cause instanceof AppException appException) {
                BaseErrorCode ec = appException.getErrorCode();
                writeResponse(response, ec.getStatus().value(), ec.getCode(), ec.getMessage());
                return;
            }
            cause = cause.getCause();
        }

        if (authException instanceof OAuth2AuthenticationException oauth2Ex) {
            OAuth2Error error = oauth2Ex.getError();
            if ("service_unavailable".equals(error.getErrorCode())) {
                writeResponse(response, HttpStatus.SERVICE_UNAVAILABLE.value(),
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode(),
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage());
                return;
            }
            if ("account_not_active".equals(error.getErrorCode())) {
                writeResponse(response, HttpStatus.FORBIDDEN.value(),
                        AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode(),
                        AuthErrorCode.ACCOUNT_NOT_ACTIVE.getMessage());
                return;
            }
            if ("session_revoked".equals(error.getErrorCode())) {
                writeResponse(response, HttpStatus.UNAUTHORIZED.value(),
                        AuthErrorCode.SESSION_REVOKED.getCode(),
                        AuthErrorCode.SESSION_REVOKED.getMessage());
                return;
            }
        }

        // Default authentication failure: 401 AUTH_001
        writeResponse(response, HttpStatus.UNAUTHORIZED.value(),
                CommonErrorCode.UNAUTHORIZED.getCode(),
                CommonErrorCode.UNAUTHORIZED.getMessage());
    }

    public void handleAccessDenied(HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn("Access denied for request [{} {}]: {}", request.getMethod(), request.getRequestURI(), accessDeniedException.getMessage());

        Object errorAttr = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        if (errorAttr instanceof BaseErrorCode baseErrorCode) {
            writeResponse(response, baseErrorCode.getStatus().value(), baseErrorCode.getCode(), baseErrorCode.getMessage());
            return;
        }

        // Default access denied: 403 AUTH_002
        writeResponse(response, HttpStatus.FORBIDDEN.value(),
                CommonErrorCode.ACCESS_DENIED.getCode(),
                CommonErrorCode.ACCESS_DENIED.getMessage());
    }

    public void writeResponse(HttpServletResponse response, int status, String code, String message) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        ApiResponse<Void> apiResponse = ApiResponse.error(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(apiResponse));
        response.getWriter().flush();
    }
}
