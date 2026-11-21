package com.aquasuite.ims.dto.servicedesk.response;

import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WarrantyPolicyResponse {
    private UUID policyId;
    private String policyCode;
    private String policyName;
    private UUID brandId;
    private String brandName;
    private UUID categoryId;
    private String categoryName;
    private Integer warrantyPeriodMonths;
    private String coverageConditions;
    private WarrantyPolicyStatus status;
    private Long variantReferenceCount;
    private Instant createdAt;
    private Instant updatedAt;
}
