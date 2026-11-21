package com.aquasuite.ims.dto.sales.request;

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
public class MarkLostRequest {

    @NotBlank(message = "Lý do thất bại không được để trống")
    private String lostReason;
}
