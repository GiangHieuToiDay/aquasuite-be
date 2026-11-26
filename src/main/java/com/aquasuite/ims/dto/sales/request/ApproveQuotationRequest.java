package com.aquasuite.ims.dto.sales.request;

import jakarta.validation.constraints.Size;
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
public class ApproveQuotationRequest {

    @Size(max = 1000, message = "Ghi chú phê duyệt tối đa 1000 ký tự")
    private String approvalNote;
}
