package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.api.CustomerRegistrationApi;
import com.aquasuite.ims.module.crm.api.dto.*;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

/** Link an existing phone profile or create one; joins IAM activation transaction atomically. */
@Service
@Validated
@RequiredArgsConstructor
public class CustomerRegistrationServiceImpl implements CustomerRegistrationApi {
    private final CustomerRepository customers;

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public CustomerRegistrationResponse createProfile(CustomerRegistrationRequest request) {
        var existing = customers.findByUserId(request.userId());
        if (existing.isPresent()) {
            Customer profile = existing.get();
            if (!profile.getPhone().equals(request.phone())
                    || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
            return new CustomerRegistrationResponse(profile.getCustomerId(), profile.getCustomerCode());
        }
        UUID id = UUID.randomUUID();
        customers.insertUnlinkedCustomer(id, "CUS-" + id, request.fullName(), request.phone(), request.email());
        // Lock the same row used by POS. Never move historical orders or overwrite CRM details.
        Customer profile = customers.findByPhoneForUpdate(request.phone())
                .orElseThrow(() -> new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION));
        if (profile.getUserId() != null || !"ACTIVE".equalsIgnoreCase(profile.getStatus())) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        profile.setUserId(request.userId());
        customers.saveAndFlush(profile);
        return new CustomerRegistrationResponse(profile.getCustomerId(), profile.getCustomerCode());
    }
}
