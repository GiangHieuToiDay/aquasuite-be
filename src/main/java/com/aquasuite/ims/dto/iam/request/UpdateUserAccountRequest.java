package com.aquasuite.ims.dto.iam.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.Set;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UpdateUserAccountRequest {
    @NotBlank @Size(max = 100) String username;
    @NotBlank @Email @Size(max = 320) String email;
    @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit local number starting with 0") String phone;
    @NotEmpty @Size(max = 50) Set<@NotNull UUID> roleIds;
    @Size(max = 2048) @Pattern(regexp = "^(https?://[^\\s]+)?$", message = "Avatar URL must start with http:// or https://") String avatarUrl;
}
