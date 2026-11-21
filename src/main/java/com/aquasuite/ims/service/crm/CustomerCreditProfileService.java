package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileResponse;

import java.util.UUID;

public interface CustomerCreditProfileService {
    CustomerCreditProfileResponse update(UUID customerId, UUID requesterId, UpdateCustomerCreditProfileRequest request);
}
