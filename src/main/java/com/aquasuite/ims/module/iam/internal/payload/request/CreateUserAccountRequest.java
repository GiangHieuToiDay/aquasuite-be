package com.aquasuite.ims.module.iam.internal.payload.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
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
public class CreateUserAccountRequest {
    @NotBlank String username;
    @NotBlank @Email String email;
    @NotBlank @Pattern(regexp = "^0\\d{9}$", message = "Phone must be a 10-digit local number starting with 0") String phone;
    @NotEmpty Set<@NotNull UUID> roleIds;
    String avatarUrl;
    boolean autoGenerateTemporaryPassword;
    String temporaryPassword;
    String confirmTemporaryPassword;
}
