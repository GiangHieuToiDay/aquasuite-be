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

/** Minimal CRM profile creation; joins IAM's transaction so profile and activation commit together. */
@Service
@Validated
@RequiredArgsConstructor
public class CustomerRegistrationServiceImpl implements CustomerRegistrationApi {
    private final CustomerRepository customers;

    @Override
    @Transactional(propagation=Propagation.MANDATORY)
    public CustomerRegistrationResponse createProfile(CustomerRegistrationRequest request) {
        var existing=customers.findByUserId(request.userId());
        if (existing.isPresent()) {
            Customer profile=existing.get();
            if (!profile.getName().equals(request.fullName()) || !profile.getPhone().equals(request.phone())
                    || !profile.getEmail().equalsIgnoreCase(request.email())) {
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
            return new CustomerRegistrationResponse(profile.getId(),profile.getCode());
        }
        Customer profile=new Customer();
        UUID id=UUID.randomUUID();
        profile.setId(id); profile.setUserId(request.userId()); profile.setCode("CUS-"+id);
        profile.setName(request.fullName()); profile.setPhone(request.phone()); profile.setEmail(request.email());
        profile.setType("RETAIL"); profile.setStatus("ACTIVE");
        customers.saveAndFlush(profile);
        return new CustomerRegistrationResponse(id,profile.getCode());
    }
}
