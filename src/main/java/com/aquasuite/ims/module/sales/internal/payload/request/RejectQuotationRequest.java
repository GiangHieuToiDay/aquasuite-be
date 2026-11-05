package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RejectQuotationRequest {

    @NotBlank(message = "Lý do từ chối không được để trống")
    private String approvalNote;
}
