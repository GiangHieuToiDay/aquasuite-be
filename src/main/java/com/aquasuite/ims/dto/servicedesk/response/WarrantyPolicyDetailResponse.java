package com.aquasuite.ims.dto.servicedesk.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WarrantyPolicyDetailResponse {
    private WarrantyPolicyResponse policy;
    private List<WarrantyPolicyVariantResponse> variants;
}
