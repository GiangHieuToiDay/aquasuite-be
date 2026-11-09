package com.aquasuite.ims.module.finance.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Yêu cầu từ chối một khoản chuyển khoản đang chờ (UC-70).
 * Lý do là bắt buộc theo quy tắc nghiệp vụ đã chốt.
 *
 * @param reason lý do từ chối, không được để trống, tối đa 1000 ký tự
 */
public record RejectPaymentRequest(
        @NotBlank(message = "Lý do từ chối là bắt buộc") @Size(max = 1000) String reason) {}
