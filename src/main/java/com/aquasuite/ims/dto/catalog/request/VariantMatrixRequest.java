package com.aquasuite.ims.dto.catalog.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record VariantMatrixRequest(@NotEmpty List<@Valid VariantUpsertRequest> variants) {
}
