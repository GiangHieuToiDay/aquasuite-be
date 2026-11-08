package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.payload.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileResponse;

import java.util.UUID;

public interface CustomerCreditProfileService {
    CustomerCreditProfileResponse update(UUID customerId, UUID requesterId, UpdateCustomerCreditProfileRequest request);
}
