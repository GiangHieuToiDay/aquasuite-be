package com.aquasuite.ims.dto.catalog.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BrandResponse {

    private UUID id;
    private String code;
    private String name;
    private String description;
    private String status;
}
