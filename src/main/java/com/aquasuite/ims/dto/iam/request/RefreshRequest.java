package com.aquasuite.ims.dto.iam.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
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
public class RefreshRequest {

    @NotBlank(message = "Refresh token không được để trống")
    @Size(max = 2048, message = "Refresh token không hợp lệ")
    @JsonProperty("refresh_token")
    private String refreshToken;
}
