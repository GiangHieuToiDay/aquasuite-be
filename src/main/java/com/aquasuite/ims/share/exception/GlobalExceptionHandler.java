package com.aquasuite.ims.share.exception;

import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.FieldErrorDetail;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("Business exception [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity
                .status(ex.getErrorCode().getStatus())
                .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        List<FieldErrorDetail> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldErrorDetail(error.getField(), error.getDefaultMessage()))
                .toList();

        return ResponseEntity
                .status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.VALIDATION_ERROR.getCode(),
                        CommonErrorCode.VALIDATION_ERROR.getMessage(),
                        errors
                ));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        log.warn("Message not readable: {}", ex.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.VALIDATION_ERROR.getCode(),
                        CommonErrorCode.VALIDATION_ERROR.getMessage()
                ));
    }

    @ExceptionHandler({
            MissingServletRequestParameterException.class,
            HandlerMethodValidationException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidRequestParamException(Exception ex) {
        log.warn("Invalid request parameter: {}", ex.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_REQUEST_PARAM.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.INVALID_REQUEST_PARAM.getCode(),
                        CommonErrorCode.INVALID_REQUEST_PARAM.getMessage()
                ));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.ACCESS_DENIED.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.ACCESS_DENIED.getCode(),
                        CommonErrorCode.ACCESS_DENIED.getMessage()
                ));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        return ResponseEntity
                .status(CommonErrorCode.DATA_INTEGRITY_VIOLATION.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                        CommonErrorCode.DATA_INTEGRITY_VIOLATION.getMessage()
                ));
    }

    @ExceptionHandler(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodArgumentTypeMismatch(org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
        log.warn("Method argument type mismatch for param [{}]: {}", ex.getName(), ex.getMessage());
        String detailMessage = String.format("Tham số '%s' không hợp lệ hoặc không đúng định dạng UUID/kiểu dữ liệu", ex.getName());
        return ResponseEntity
                .status(CommonErrorCode.INVALID_REQUEST_PARAM.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.INVALID_REQUEST_PARAM.getCode(),
                        detailMessage
                ));
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Void>> handleMaxUploadSizeExceeded(org.springframework.web.multipart.MaxUploadSizeExceededException ex) {
        log.warn("Max upload size exceeded: {}", ex.getMessage());
        return ResponseEntity
                .status(org.springframework.http.HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(
                        "MSG17",
                        "Avatar must be a JPEG, PNG, or WEBP image under 5MB."
                ));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleFallbackException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity
                .status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(ApiResponse.error(
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                        CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()
                ));
    }
}
