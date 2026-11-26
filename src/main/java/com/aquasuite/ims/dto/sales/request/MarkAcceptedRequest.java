package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarkAcceptedRequest {

    @Size(max = 2048, message = "URL tài liệu đã ký tối đa 2048 ký tự")
    @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "URL tài liệu đã ký phải bắt đầu bằng http:// hoặc https://")
    private String signedFileUrl;
}
