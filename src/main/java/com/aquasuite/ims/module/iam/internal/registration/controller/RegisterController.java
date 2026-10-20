package com.aquasuite.ims.module.iam.internal.registration.controller;

import com.aquasuite.ims.module.iam.internal.auth.dto.response.LoginResponse;
import com.aquasuite.ims.module.iam.internal.registration.payload.*;
import com.aquasuite.ims.module.iam.internal.registration.service.RegisterService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.FieldErrorDetail;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

/** Guest-facing CUSTOMER account registration, email verification and resend operations. */
@RestController
@RequestMapping("/api/v1/customer-registrations")
@RequiredArgsConstructor
public class RegisterController {
    private final RegisterService registrations;

    @PostMapping
    public ResponseEntity<ApiResponse<RegisterResponse>> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(201).cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(registrations.register(request), "Please verify the code sent to your email."));
    }

    @PostMapping("/verifications")
    public ResponseEntity<ApiResponse<LoginResponse>> verify(@Valid @RequestBody VerifyRegistrationRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(registrations.verify(request),
                        "Account registered successfully! Welcome to AquaSuite."));
    }

    @PostMapping("/otp-resends")
    public ResponseEntity<ApiResponse<Void>> resend(@Valid @RequestBody ResendRegistrationOtpRequest request) {
        registrations.resend(request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok(null, "A verification code has been sent to your email."));
    }

    /** Avoid the global validation logger, whose exception text includes rejected credentials. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> validation(MethodArgumentNotValidException error) {
        var fields = error.getBindingResult().getFieldErrors().stream()
                .map(e -> new FieldErrorDetail(e.getField(), e.getDefaultMessage())).toList();
        return ResponseEntity.badRequest().cacheControl(CacheControl.noStore()).body(ApiResponse.error(
                CommonErrorCode.VALIDATION_ERROR.getCode(), CommonErrorCode.VALIDATION_ERROR.getMessage(), fields));
    }
}
