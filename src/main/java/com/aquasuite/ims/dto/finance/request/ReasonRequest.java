package com.aquasuite.ims.dto.finance.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReasonRequest(@NotBlank @Size(max = 500) String reason) {}
