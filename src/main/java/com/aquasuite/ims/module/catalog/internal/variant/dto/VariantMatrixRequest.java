package com.aquasuite.ims.module.catalog.internal.variant.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record VariantMatrixRequest(@NotEmpty List<@Valid VariantUpsertRequest> variants) { }
