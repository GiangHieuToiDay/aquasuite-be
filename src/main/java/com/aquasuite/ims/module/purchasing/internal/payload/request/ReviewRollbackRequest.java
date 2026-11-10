package com.aquasuite.ims.module.purchasing.internal.payload.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewRollbackRequest {

    @NotNull(message = "Kết quả phê duyệt không được để trống")
    private Boolean approved;

    private String reviewNote;
}

