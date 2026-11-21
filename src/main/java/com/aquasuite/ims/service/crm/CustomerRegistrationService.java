package com.aquasuite.ims.service.crm;

import com.aquasuite.ims.dto.crm.request.CustomerRegistrationRequest;
import com.aquasuite.ims.dto.crm.response.CustomerRegistrationResponse;

/** Creates the customer profile in the caller's activation transaction; exposes no CRM entity. */
public interface CustomerRegistrationService {
    CustomerRegistrationResponse createProfile(
            @jakarta.validation.constraints.NotNull @jakarta.validation.Valid
                    CustomerRegistrationRequest request);
}
