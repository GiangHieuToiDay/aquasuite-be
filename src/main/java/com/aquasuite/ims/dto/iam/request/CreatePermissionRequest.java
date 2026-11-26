package com.aquasuite.ims.dto.iam.request;

import com.fasterxml.jackson.annotation.JsonAnySetter;
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
public class CreatePermissionRequest {
    private String code;
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;

    @JsonAnySetter
    public void handleUnknown(String key, Object value) {
        throw new IllegalArgumentException("Unknown property: " + key);
    }
}
