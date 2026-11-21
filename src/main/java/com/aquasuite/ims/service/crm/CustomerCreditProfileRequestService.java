package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.enums.crm.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.dto.crm.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestDetailResponse;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.dto.crm.response.CreditEvidenceDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerCreditProfileRequestService {
    CustomerCreditProfileRequestResponse submit(UUID customerId, SubmitCustomerCreditProfileRequest request);

    Page<CustomerCreditProfileRequestResponse> list(CustomerCreditProfileRequestStatus status, Pageable pageable);

    CustomerCreditProfileRequestDetailResponse detail(UUID requestId);

    CreditEvidenceDocument readIdentityDocument(UUID requestId);

    CustomerCreditProfileRequestResponse approve(UUID requestId, ApproveCustomerCreditProfileRequest request);

    CustomerCreditProfileRequestResponse reject(UUID requestId, RejectCustomerCreditProfileRequest request);
}
