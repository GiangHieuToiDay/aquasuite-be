package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.internal.payload.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.payload.request.RegisterRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.RegisterResponse;
import com.aquasuite.ims.module.iam.internal.payload.request.ResendRegistrationOtpRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.VerifyRegistrationRequest;
import com.aquasuite.ims.module.iam.internal.service.RegisterService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.FieldErrorDetail;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/**
 * Cung cấp API đăng ký tài khoản CUSTOMER, xác thực và gửi lại OTP qua email cho khách chưa đăng nhập.
 * Các phản hồi không được cache vì có thể chứa thông tin đăng ký hoặc token đăng nhập.
 */
@RestController
@RequestMapping("/api/v1/iam/register")
@RequiredArgsConstructor
public class RegisterController {
    private final RegisterService registrations;

    /** Tiếp nhận form đăng ký, gửi email OTP và trả về phiên chờ xác thực; chưa tạo tài khoản. */
    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> register(
            @Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201)
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.ok(
                                registrations.register(request),
                                "Please verify the code sent to your email."));
    }

    /** Xác thực OTP của phiên được tra theo email, tạo tài khoản và trả về kết quả đăng nhập. */
    @PostMapping("/verifications")
    public ResponseEntity<ApiResponse<LoginResponse>> verify(
            @Valid @RequestBody VerifyRegistrationRequest request) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.ok(
                                registrations.verify(request),
                                "Account registered successfully! Welcome to AquaSuite."));
    }

    /** Gửi lại OTP tới email đã lưu trong phiên đăng ký, áp dụng thời gian chờ và giới hạn gửi. */
    @PostMapping("/otp-resends")
    public ResponseEntity<ApiResponse<Void>> resend(
            @Valid @RequestBody ResendRegistrationOtpRequest request) {
        registrations.resend(request);
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(null, "A verification code has been sent to your email."));
    }

    /** Trả lỗi validation theo từng trường, tránh logger toàn cục có thể ghi giá trị credential bị từ chối. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error) {
        var fields =
                error.getBindingResult().getFieldErrors().stream()
                        .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage()))
                        .toList();
        return ResponseEntity.badRequest()
                .cacheControl(CacheControl.noStore())
                .body(
                        ApiResponse.error(
                                CommonErrorCode.VALIDATION_ERROR.getCode(),
                                CommonErrorCode.VALIDATION_ERROR.getMessage(),
                                fields));
    }
}
