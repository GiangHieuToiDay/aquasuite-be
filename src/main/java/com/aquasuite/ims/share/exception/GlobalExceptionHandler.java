package com.aquasuite.ims.share.exception;

import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.FieldErrorDetail;

import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiResponse<Void>> handleAppException(AppException ex) {
        log.warn("Business exception [{}]: {}", ex.getErrorCode().getCode(), ex.getMessage());
        return ResponseEntity.status(ex.getErrorCode().getStatus())
                .body(ApiResponse.error(ex.getErrorCode().getCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(
            MethodArgumentNotValidException ex) {
        log.warn("Validation error: {}", ex.getMessage());
        List<FieldErrorDetail> errors =
                ex.getBindingResult().getFieldErrors().stream()
                        .map(
                                error ->
                                        new FieldErrorDetail(
                                                error.getField(), error.getDefaultMessage()))
                        .toList();

        return ResponseEntity.status(CommonErrorCode.VALIDATION_ERROR.getStatus())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.VALIDATION_ERROR.getCode(),
                                CommonErrorCode.VALIDATION_ERROR.getMessage(),
                                errors));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return ResponseEntity.status(CommonErrorCode.ACCESS_DENIED.getStatus())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.ACCESS_DENIED.getCode(),
                                CommonErrorCode.ACCESS_DENIED.getMessage()));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException ex) {
        log.error("Data integrity violation: ", ex);
        return ResponseEntity.status(CommonErrorCode.DATA_INTEGRITY_VIOLATION.getStatus())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                                CommonErrorCode.DATA_INTEGRITY_VIOLATION.getMessage()));
    }

    @ExceptionHandler({
        org.springframework.http.converter.HttpMessageNotReadableException.class,
        org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
        org.springframework.web.bind.MissingServletRequestParameterException.class,
        org.springframework.web.bind.ServletRequestBindingException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleInvalidBinding(Exception ex) {
        return ResponseEntity.badRequest()
                .body(
                        ApiResponse.error(
                                CommonErrorCode.INVALID_REQUEST_PARAM.getCode(),
                                CommonErrorCode.INVALID_REQUEST_PARAM.getMessage()));
    }

    @ExceptionHandler(org.springframework.dao.OptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticConflict(Exception ex) {
        if (ex instanceof org.springframework.orm.ObjectOptimisticLockingFailureException conflict
                && "com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration"
                        .equals(conflict.getPersistentClassName())) {
            return ResponseEntity.status(409)
                    .body(
                            ApiResponse.error(
                                    "CATALOG_BUNDLE_003",
                                    "Combo đã được thay đổi, vui lòng tải lại"));
        }
        return ResponseEntity.status(409)
                .body(
                        ApiResponse.error(
                                CommonErrorCode.DATA_INTEGRITY_VIOLATION.getCode(),
                                CommonErrorCode.DATA_INTEGRITY_VIOLATION.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleFallbackException(Exception ex) {
        log.error("Unhandled exception: ", ex);
        return ResponseEntity.status(CommonErrorCode.INTERNAL_SERVER_ERROR.getStatus())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.INTERNAL_SERVER_ERROR.getCode(),
                                CommonErrorCode.INTERNAL_SERVER_ERROR.getMessage()));
    }
}
