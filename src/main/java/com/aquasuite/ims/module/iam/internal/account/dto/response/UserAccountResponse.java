package com.aquasuite.ims.module.iam.internal.account.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class UserAccountResponse {
    UUID id;
    String username;
    String email;
    String phone;
    String avatarUrl;
    List<RoleOptionResponse> roles;
    String status;
    String provider;
    @JsonProperty("isVerified") boolean verified;
    Instant createdAt;
    boolean currentUser;

    @JsonIgnore
    public boolean isVerified() {
        return verified;
    }

    @JsonIgnore
    public void setVerified(boolean verified) {
        this.verified = verified;
    }
}
