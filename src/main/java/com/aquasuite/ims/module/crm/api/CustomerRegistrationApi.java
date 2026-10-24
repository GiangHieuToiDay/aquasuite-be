package com.aquasuite.ims.module.crm.api;

import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationRequest;
import com.aquasuite.ims.module.crm.api.dto.CustomerRegistrationResponse;

/** Creates the customer profile in the caller's activation transaction; exposes no CRM entity. */
public interface CustomerRegistrationApi {
    CustomerRegistrationResponse createProfile(
            @jakarta.validation.constraints.NotNull @jakarta.validation.Valid
                    CustomerRegistrationRequest request);
}
