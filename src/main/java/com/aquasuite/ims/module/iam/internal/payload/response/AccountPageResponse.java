package com.aquasuite.ims.module.iam.internal.payload.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountPageResponse {
    List<UserAccountResponse> content;
    int page;
    int size;
    long totalElements;
    int totalPages;
}
