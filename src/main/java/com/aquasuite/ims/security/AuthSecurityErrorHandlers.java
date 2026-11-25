package com.aquasuite.ims.security;

import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.BaseErrorCode;
import com.aquasuite.ims.exception.CommonErrorCode;
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

/**
 * Chuẩn hóa response lỗi bảo mật thành JSON {@link ApiResponse}: 401 khi chưa xác thực (entry point)
 * và 403 khi không đủ quyền (access denied handler). Mã lỗi cụ thể có thể được các bộ lọc hoặc
 * converter gắn trước vào request qua {@link #AUTH_ERROR_ATTRIBUTE}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthSecurityErrorHandlers {

    /** Tên attribute trong request dùng để chuyển mã lỗi xác thực từ decoder/converter sang handler. */
    public static final String AUTH_ERROR_ATTRIBUTE = "AQUASUITE_AUTH_ERROR";

    private final ObjectMapper objectMapper;

    /** Trả về entry point xử lý request chưa xác thực hoặc token không hợp lệ (401). */
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return this::commence;
    }

    /** Trả về handler xử lý request đã xác thực nhưng không đủ quyền (403). */
    public AccessDeniedHandler accessDeniedHandler() {
        return this::handleAccessDenied;
    }

    /**
     * Xử lý lỗi xác thực: ưu tiên mã lỗi đã gắn trong request, rồi đến AppException nằm trong chuỗi
     * nguyên nhân, rồi đến mã lỗi OAuth2 (dịch vụ không khả dụng, tài khoản không hoạt động, phiên bị
     * thu hồi); mặc định trả 401.
     */
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException)
            throws IOException {
        log.warn(
                "Authentication failed for request [{} {}]: {}",
                request.getMethod(),
                request.getRequestURI(),
                authException.getMessage());

        // Ưu tiên mã lỗi do converter hoặc decoder đã gắn sẵn vào request
        Object errorAttr = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        if (errorAttr instanceof BaseErrorCode baseErrorCode) {
            writeResponse(
                    response,
                    baseErrorCode.getStatus().value(),
                    baseErrorCode.getCode(),
                    baseErrorCode.getMessage());
            return;
        }

        // Tìm AppException trong chuỗi nguyên nhân của lỗi xác thực
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
                writeResponse(
                        response,
                        HttpStatus.SERVICE_UNAVAILABLE.value(),
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getCode(),
                        AuthErrorCode.AUTH_SERVICE_UNAVAILABLE.getMessage());
                return;
            }
            if ("account_not_active".equals(error.getErrorCode())) {
                writeResponse(
                        response,
                        HttpStatus.FORBIDDEN.value(),
                        AuthErrorCode.ACCOUNT_NOT_ACTIVE.getCode(),
                        AuthErrorCode.ACCOUNT_NOT_ACTIVE.getMessage());
                return;
            }
            if ("session_revoked".equals(error.getErrorCode())) {
                writeResponse(
                        response,
                        HttpStatus.UNAUTHORIZED.value(),
                        AuthErrorCode.SESSION_REVOKED.getCode(),
                        AuthErrorCode.SESSION_REVOKED.getMessage());
                return;
            }
        }

        // Mặc định: lỗi xác thực 401 (AUTH_001)
        writeResponse(
                response,
                HttpStatus.UNAUTHORIZED.value(),
                CommonErrorCode.UNAUTHORIZED.getCode(),
                CommonErrorCode.UNAUTHORIZED.getMessage());
    }

    /** Xử lý lỗi không đủ quyền: dùng mã lỗi đã gắn trong request nếu có, mặc định trả 403. */
    public void handleAccessDenied(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException accessDeniedException)
            throws IOException {
        log.warn(
                "Access denied for request [{} {}]: {}",
                request.getMethod(),
                request.getRequestURI(),
                accessDeniedException.getMessage());

        Object errorAttr = request.getAttribute(AUTH_ERROR_ATTRIBUTE);
        if (errorAttr instanceof BaseErrorCode baseErrorCode) {
            writeResponse(
                    response,
                    baseErrorCode.getStatus().value(),
                    baseErrorCode.getCode(),
                    baseErrorCode.getMessage());
            return;
        }

        // Mặc định: không đủ quyền 403 (AUTH_002)
        writeResponse(
                response,
                HttpStatus.FORBIDDEN.value(),
                CommonErrorCode.ACCESS_DENIED.getCode(),
                CommonErrorCode.ACCESS_DENIED.getMessage());
    }

    /** Ghi response lỗi dạng JSON UTF-8; bỏ qua nếu response đã được gửi đi. */
    public void writeResponse(HttpServletResponse response, int status, String code, String message)
            throws IOException {
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
