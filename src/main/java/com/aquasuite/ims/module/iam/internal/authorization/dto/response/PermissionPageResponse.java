package com.aquasuite.ims.module.iam.internal.authorization.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PermissionPageResponse {
    private List<PermissionResponse> items;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
}
