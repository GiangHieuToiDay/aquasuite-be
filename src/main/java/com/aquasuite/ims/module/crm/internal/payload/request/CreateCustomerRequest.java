package com.aquasuite.ims.module.crm.internal.payload.request;

import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
        @NotBlank(message = "Tên khách hàng không được để trống")
        @Size(min = 2, max = 150, message = "Tên khách hàng phải từ 2 đến 150 ký tự")
        String customerName,

        @Pattern(regexp = "^(0[35789][0-9]{8})?$", message = "Số điện thoại không đúng định dạng Việt Nam (10 số, bắt đầu bằng 03, 05, 07, 08, 09)")
        String phone,

        @Email(message = "Email không đúng định dạng")
        @Size(max = 320, message = "Email không được vượt quá 320 ký tự")
        String email,

        CustomerType customerType,

        @Size(max = 50, message = "Mã số thuế không được vượt quá 50 ký tự")
        String taxCode,

        @Size(max = 255, message = "Tên công ty không được vượt quá 255 ký tự")
        String companyName,

        @Size(max = 500, message = "Địa chỉ không được vượt quá 500 ký tự")
        String address
) {
}

