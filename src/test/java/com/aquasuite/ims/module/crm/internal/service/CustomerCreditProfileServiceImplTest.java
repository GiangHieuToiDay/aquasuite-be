package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileUpdatedEvent;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.payload.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.impl.CustomerCreditProfileServiceImpl;
import com.aquasuite.ims.module.iam.api.CreditApprovalGrantApi;
import com.aquasuite.ims.module.iam.api.dto.CreditApprovalGrantDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerCreditProfileServiceImplTest {
    @Mock private CustomerRepository customerRepository;
    @Mock private CreditApprovalGrantApi approvalGrantApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void approvedGrantUpdatesProfileAndAuditsInitiatorAndApprover() {
        UUID customerId = UUID.randomUUID();
        UUID requesterId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        Customer customer = Customer.builder().customerId(customerId).customerCode("KH-1")
                .customerName("An Khang").phone("0900000000").status("ACTIVE").creditLimit(BigDecimal.ZERO).build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        when(approvalGrantApi.consumeCreditProfileGrant(eq(requesterId), eq("grant"), eq(customerId),
                eq(new BigDecimal("20000000")), eq(15))).thenReturn(new CreditApprovalGrantDto(ownerId, requesterId, customerId));
        CustomerCreditProfileServiceImpl service = new CustomerCreditProfileServiceImpl(customerRepository, approvalGrantApi, eventPublisher);

        var result = service.update(customerId, requesterId, request());

        assertEquals(new BigDecimal("20000000"), result.creditLimit());
        assertEquals(15, result.creditTermDays());
        ArgumentCaptor<CustomerCreditProfileUpdatedEvent> event = ArgumentCaptor.forClass(CustomerCreditProfileUpdatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertEquals(ownerId, event.getValue().approvedBy());
        assertEquals(requesterId, event.getValue().requestedBy());
    }

    @Test
    void blockedCustomerDoesNotConsumeAnApprovalGrant() {
        UUID customerId = UUID.randomUUID();
        Customer customer = Customer.builder().customerId(customerId).customerCode("KH-1")
                .customerName("Blocked").phone("0900000000").status("BLOCKED").build();
        when(customerRepository.findById(customerId)).thenReturn(Optional.of(customer));
        CustomerCreditProfileServiceImpl service = new CustomerCreditProfileServiceImpl(customerRepository, approvalGrantApi, eventPublisher);

        assertThrows(AppException.class, () -> service.update(customerId, UUID.randomUUID(), request()));
        verify(approvalGrantApi, never()).consumeCreditProfileGrant(any(), any(), any(), any(), any());
    }

    private UpdateCustomerCreditProfileRequest request() {
        UpdateCustomerCreditProfileRequest request = new UpdateCustomerCreditProfileRequest();
        request.setCreditLimit(new BigDecimal("20000000"));
        request.setCreditTermDays(15);
        request.setApprovalToken("grant");
        request.setReason("Duyệt tại quầy");
        return request;
    }
}
