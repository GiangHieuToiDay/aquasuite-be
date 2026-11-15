package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyPolicyStatus;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyReferenceDataResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.UUID;

public interface WarrantyPolicyService {
    PageResponse<WarrantyPolicyResponse> search(String keyword, WarrantyPolicyStatus status, Pageable pageable);
    WarrantyPolicyDetailResponse detail(UUID policyId);
    WarrantyPolicyResponse create(UpsertWarrantyPolicyRequest request);
    WarrantyPolicyResponse update(UUID policyId, UpsertWarrantyPolicyRequest request);
    WarrantyPolicyResponse deactivate(UUID policyId);
    void delete(UUID policyId);
    WarrantyPolicyReferenceDataResponse referenceData();
}
