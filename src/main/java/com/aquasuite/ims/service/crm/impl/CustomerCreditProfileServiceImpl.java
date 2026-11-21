package com.aquasuite.ims.service.crm.impl;

import com.aquasuite.ims.event.crm.CustomerCreditProfileUpdatedEvent;
import com.aquasuite.ims.entity.crm.Customer;
import com.aquasuite.ims.exception.crm.CrmErrorCode;
import com.aquasuite.ims.dto.crm.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileResponse;
import com.aquasuite.ims.repository.crm.CustomerRepository;
import com.aquasuite.ims.service.crm.CustomerCreditProfileService;
import com.aquasuite.ims.service.iam.CreditApprovalGrantService;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantDto;
import com.aquasuite.ims.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerCreditProfileServiceImpl implements CustomerCreditProfileService {
    private static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("500000000.00");
    private final CustomerRepository customerRepository;
    private final CreditApprovalGrantService creditApprovalGrantApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CustomerCreditProfileResponse update(UUID customerId, UUID requesterId, UpdateCustomerCreditProfileRequest request) {
        validate(request);
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new AppException(CrmErrorCode.CUSTOMER_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new AppException(CrmErrorCode.CUSTOMER_NOT_ACTIVE_FOR_CREDIT);
        }
        // Consume only after the deterministic local validation so the owner need not re-authenticate for a bad request.
        CreditApprovalGrantDto approval = creditApprovalGrantApi.consumeCreditProfileGrant(requesterId,
                request.getApprovalToken(), customerId, request.getCreditLimit(), request.getCreditTermDays());
        BigDecimal oldLimit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        Integer oldTerm = customer.getCreditTermDays();
        customer.setCreditLimit(request.getCreditLimit());
        customer.setCreditTermDays(request.getCreditTermDays());
        customerRepository.save(customer);
        eventPublisher.publishEvent(new CustomerCreditProfileUpdatedEvent(UUID.randomUUID(), customerId, requesterId,
                approval.approverId(), oldLimit, oldTerm, request.getCreditLimit(), request.getCreditTermDays(),
                request.getReason().trim(), Instant.now()));
        return CustomerCreditProfileResponse.builder().customerId(customerId).creditLimit(customer.getCreditLimit())
                .creditTermDays(customer.getCreditTermDays()).requestedBy(requesterId)
                .approvedBy(approval.approverId()).build();
    }

    private void validate(UpdateCustomerCreditProfileRequest request) {
        if (request.getCreditLimit() == null || request.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0
                || request.getCreditTermDays() == null || request.getCreditTermDays() < 1 || request.getCreditTermDays() > 30) {
            throw new AppException(CrmErrorCode.INVALID_CREDIT_PROFILE);
        }
        if (request.getCreditLimit().compareTo(MAX_CREDIT_LIMIT) > 0) {
            throw new AppException(CrmErrorCode.CREDIT_LIMIT_EXCEEDS_MAXIMUM);
        }
    }
}
