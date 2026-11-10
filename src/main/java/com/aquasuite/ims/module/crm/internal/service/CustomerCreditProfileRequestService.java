package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.module.crm.internal.payload.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestDetailResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.UUID;

public interface CustomerCreditProfileRequestService {
    CustomerCreditProfileRequestResponse submit(UUID customerId, SubmitCustomerCreditProfileRequest request);

    Page<CustomerCreditProfileRequestResponse> list(CustomerCreditProfileRequestStatus status, Pageable pageable);

    CustomerCreditProfileRequestDetailResponse detail(UUID requestId);

    CustomerCreditProfileRequestResponse approve(UUID requestId, ApproveCustomerCreditProfileRequest request);

    CustomerCreditProfileRequestResponse reject(UUID requestId, RejectCustomerCreditProfileRequest request);
}
