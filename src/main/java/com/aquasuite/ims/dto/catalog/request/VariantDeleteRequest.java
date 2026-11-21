package com.aquasuite.ims.dto.catalog.request;

import jakarta.validation.constraints.Size;

public record VariantDeleteRequest(boolean confirmed, @Size(max = 500) String reason) {
}