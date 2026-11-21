package com.aquasuite.ims.service.crm.impl;
import com.aquasuite.ims.dto.crm.request.CustomerRegistrationRequest;
import com.aquasuite.ims.dto.crm.response.CustomerRegistrationResponse;

import com.aquasuite.ims.service.crm.CustomerRegistrationService;
import com.aquasuite.ims.dto.crm.response.*;
import com.aquasuite.ims.entity.crm.Customer;
import com.aquasuite.ims.repository.crm.CustomerRepository;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import org.springframework.validation.annotation.Validated;
import java.util.UUID;

/** Link an existing phone profile or create one; joins IAM activation transaction atomically. */
@Service
@Validated
@RequiredArgsConstructor
public class CustomerRegistrationServiceImpl implements CustomerRegistrationService {
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
