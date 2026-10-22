package com.aquasuite.ims.module.catalog.internal.payload.res;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private String status;
    private UUID parentId;
    private Instant createdAt;
    private Instant updatedAt;
}
