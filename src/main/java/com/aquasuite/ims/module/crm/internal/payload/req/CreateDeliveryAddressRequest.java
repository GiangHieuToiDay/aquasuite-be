package com.aquasuite.ims.module.crm.internal.payload.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
public record CreateDeliveryAddressRequest(
        @NotBlank(message = "Tên người nhận không được để trống")
        @Size(max = 100, message = "Tên người nhận tối đa 100 ký tự")
        String recipientName,

        @NotBlank(message = "Số điện thoại nhận hàng không được để trống")
        @Pattern(regexp = "^0\\d{9}$", message = "Số điện thoại nhận hàng không hợp lệ, phải gồm 10 chữ số")
        String phone,

        @NotBlank(message = "Tỉnh/Thành phố không được để trống")
        @Size(max = 100, message = "Tên Tỉnh/Thành phố tối đa 100 ký tự")
        String province,

        @NotBlank(message = "Quận/Huyện không được để trống")
        @Size(max = 100, message = "Tên Quận/Huyện tối đa 100 ký tự")
        String district,

        @NotBlank(message = "Phường/Xã không được để trống")
        @Size(max = 100, message = "Tên Phường/Xã tối đa 100 ký tự")
        String ward,

        @NotBlank(message = "Địa chỉ chi tiết không được để trống")
        @Size(max = 255, message = "Địa chỉ chi tiết tối đa 255 ký tự")
        String streetAddress,

        @Size(max = 50, message = "Nhãn địa chỉ tối đa 50 ký tự")
        String label, // Home, Construction Site, Villa, Office...

        Boolean isDefault
) {}
