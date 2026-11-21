package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import com.aquasuite.ims.dto.servicedesk.request.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyReferenceDataResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicyResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.aquasuite.ims.common.PageResponse;

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
