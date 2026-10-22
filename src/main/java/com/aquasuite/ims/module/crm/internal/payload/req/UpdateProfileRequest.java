package com.aquasuite.ims.module.crm.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record UpdateProfileRequest(
        @NotBlank(message = "Họ và tên không được để trống")
        @Size(max = 150, message = "Họ và tên không được vượt quá 150 ký tự")
        String fullName,

        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại không hợp lệ, phải gồm 10 chữ số và bắt đầu bằng số 0")
        String phone,

        @Size(max = 2048, message = "URL ảnh đại diện không hợp lệ")
        String avatarUrl
) {}
