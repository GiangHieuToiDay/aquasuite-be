package com.aquasuite.ims.dto.catalog.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record VariantMatrixRequest(
        @NotEmpty @Size(max = 200) List<@NotNull @Valid VariantUpsertRequest> variants) {
}
