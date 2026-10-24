package com.aquasuite.ims.module.crm.api;

import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous interface for the CRM Module (FE-10).
 * Other modules (Sales, ServiceDesk, Finance, IAM) communicate with CRM exclusively via this contract.
 */
public interface CrmApi {

    Optional<CustomerSummaryDto> getCustomerByUserId(UUID userId);

    Optional<CustomerSummaryDto> getCustomerById(UUID customerId);

    Optional<CustomerSummaryDto> getCustomerByPhone(String phone);

    List<CustomerAddressDto> getDeliveryAddressesByUserId(UUID userId);

    List<CustomerAddressDto> getDeliveryAddressesByCustomerId(UUID customerId);

    Optional<CustomerAddressDto> getDefaultDeliveryAddress(UUID customerId);
}
