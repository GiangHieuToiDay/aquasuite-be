package com.aquasuite.ims.module.catalog.internal.payload.req;

import jakarta.validation.constraints.Size;

public record VariantDeleteRequest(boolean confirmed, @Size(max = 500) String reason) {
}