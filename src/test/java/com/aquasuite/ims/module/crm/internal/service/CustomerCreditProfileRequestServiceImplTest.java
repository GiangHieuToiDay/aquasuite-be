package com.aquasuite.ims.module.crm.internal.service;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.mapper.CustomerCreditProfileRequestMapper;
import com.aquasuite.ims.module.crm.internal.payload.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerCreditProfileRequestRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.impl.CustomerCreditProfileRequestServiceImpl;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.sales.api.CustomerOrderHistoryApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class CustomerCreditProfileRequestServiceImplTest {

    @Mock private CustomerRepository customerRepository;
    @Mock private CustomerCreditProfileRequestRepository requestRepository;
    @Mock private CustomerCreditProfileRequestMapper requestMapper;
    @Mock private CustomerOrderHistoryApi customerOrderHistoryApi;
    @Mock private IamApi iamApi;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private CustomerCreditProfileRequestServiceImpl service;

    private final UUID customerId = UUID.randomUUID();
    private final UUID salespersonId = UUID.randomUUID();
    private final UUID ownerId = UUID.randomUUID();
    private Customer customer;

    @BeforeEach
    void setUp() {
        customer = Customer.builder()
                .customerId(customerId)
                .customerType(CustomerType.RETAIL)
                .status("ACTIVE")
                .creditLimit(BigDecimal.ZERO)
                .build();
        lenient().when(customerRepository.findByIdForUpdate(customerId)).thenReturn(Optional.of(customer));
        lenient().when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(0L);
        lenient().when(requestRepository.save(any(CustomerCreditProfileRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(requestRepository.saveAndFlush(any(CustomerCreditProfileRequest.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(requestMapper.toResponse(any(CustomerCreditProfileRequest.class))).thenAnswer(invocation -> responseFor(invocation.getArgument(0)));
    }

    @Test
    void sales_autoAppliesTrustedRetailCustomerUpToTwoMillion() {
        when(iamApi.getCurrentUser()).thenReturn(user(salespersonId, "SALES_STAFF"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(3L);

        CustomerCreditProfileRequestResponse result = service.submit(customerId, submit("2000000", 15));

        assertThat(result.appliedImmediately()).isTrue();
        assertThat(result.status()).isEqualTo(CustomerCreditProfileRequestStatus.APPROVED);
        assertThat(customer.getCreditLimit()).isEqualByComparingTo("2000000");
        assertThat(customer.getCreditTermDays()).isEqualTo(15);
        verify(customerRepository).save(customer);
        verify(requestRepository, never()).saveAndFlush(any());
    }

    @Test
    void sales_submitsPendingWhenRetailAmountExceedsPolicyThreshold() {
        when(iamApi.getCurrentUser()).thenReturn(user(salespersonId, "SALES_STAFF"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(3L);

        CustomerCreditProfileRequestResponse result = service.submit(customerId, submit("2000001", 15));

        assertThat(result.appliedImmediately()).isFalse();
        assertThat(result.status()).isEqualTo(CustomerCreditProfileRequestStatus.PENDING);
        assertThat(customer.getCreditLimit()).isZero();
        verify(customerRepository, never()).save(customer);
        verify(requestRepository).saveAndFlush(any(CustomerCreditProfileRequest.class));
    }

    @Test
    void contractorWithoutEvidenceMustWaitForOwnerReview() {
        customer.setCustomerType(CustomerType.CONTRACTOR);
        when(iamApi.getCurrentUser()).thenReturn(user(salespersonId, "SALES_STAFF"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(3L);
        SubmitCustomerCreditProfileRequest request = submit("10000000", 15);

        CustomerCreditProfileRequestResponse result = service.submit(customerId, request);

        assertThat(result.status()).isEqualTo(CustomerCreditProfileRequestStatus.PENDING);
        assertThat(result.appliedImmediately()).isFalse();
        assertThat(customer.getCreditLimit()).isZero();
    }

    @Test
    void trustedProjectCustomerAutoAppliesUpToTenMillionWithCompleteEvidence() {
        customer.setCustomerType(CustomerType.PROJECT_CUSTOMER);
        when(iamApi.getCurrentUser()).thenReturn(user(salespersonId, "SALES_STAFF"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(3L);
        SubmitCustomerCreditProfileRequest request = submit("10000000", 30);
        request.setIdentityDocumentNumber("012345678901");
        request.setResidentialAddress("12 Nguyễn Văn Trỗi, Bắc Ninh");
        request.setWorksiteAddress("Công trình Riverside, Bắc Ninh");

        CustomerCreditProfileRequestResponse result = service.submit(customerId, request);

        assertThat(result.appliedImmediately()).isTrue();
        assertThat(result.identityEvidenceProvided()).isTrue();
        assertThat(customer.getCreditLimit()).isEqualByComparingTo("10000000");
    }

    @Test
    void creditLimitAboveFiveHundredMillionIsRejected() {
        assertThatThrownBy(() -> service.submit(customerId, submit("500000001", 30)))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_LIMIT_EXCEEDS_MAXIMUM);
    }

    @Test
    void ownerApprovalAppliesPendingProfileAtomically() {
        CustomerCreditProfileRequest pending = pendingRequest();
        when(requestRepository.findByIdForUpdate(pending.getRequestId())).thenReturn(Optional.of(pending));
        when(iamApi.getCurrentUser()).thenReturn(user(ownerId, "SHOP_OWNER"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(1L);
        ApproveCustomerCreditProfileRequest input = new ApproveCustomerCreditProfileRequest();
        input.setReviewNote("Đủ điều kiện theo đánh giá của chủ cửa hàng.");

        CustomerCreditProfileRequestResponse result = service.approve(pending.getRequestId(), input);

        assertThat(result.status()).isEqualTo(CustomerCreditProfileRequestStatus.APPROVED);
        assertThat(customer.getCreditLimit()).isEqualByComparingTo("5000000");
        assertThat(pending.getReviewedBy()).isEqualTo(ownerId);
        verify(customerRepository).save(customer);
    }

    @Test
    void rejectionDoesNotChangeActiveProfile() {
        CustomerCreditProfileRequest pending = pendingRequest();
        when(requestRepository.findByIdForUpdate(pending.getRequestId())).thenReturn(Optional.of(pending));
        when(iamApi.getCurrentUser()).thenReturn(user(ownerId, "SHOP_OWNER"));
        when(customerOrderHistoryApi.countQualifiedOrders(customerId)).thenReturn(1L);
        RejectCustomerCreditProfileRequest input = new RejectCustomerCreditProfileRequest();
        input.setReviewNote("Chưa đủ lịch sử giao dịch.");

        CustomerCreditProfileRequestResponse result = service.reject(pending.getRequestId(), input);

        assertThat(result.status()).isEqualTo(CustomerCreditProfileRequestStatus.REJECTED);
        assertThat(customer.getCreditLimit()).isZero();
        verify(customerRepository, never()).save(customer);
    }

    @Test
    void nonOwnerCannotApprovePendingRequestEvenWhenCallingServiceDirectly() {
        CustomerCreditProfileRequest pending = pendingRequest();
        when(iamApi.getCurrentUser()).thenReturn(user(salespersonId, "SALES_STAFF"));
        ApproveCustomerCreditProfileRequest input = new ApproveCustomerCreditProfileRequest();

        assertThatThrownBy(() -> service.approve(pending.getRequestId(), input))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verify(requestRepository, never()).findByIdForUpdate(any());
    }

    private CustomerCreditProfileRequest pendingRequest() {
        return CustomerCreditProfileRequest.builder()
                .requestId(UUID.randomUUID())
                .customerId(customerId)
                .requesterId(salespersonId)
                .requestedCreditLimit(new BigDecimal("5000000"))
                .requestedCreditTermDays(20)
                .reason("Khách cần công nợ cho đơn công trình.")
                .status(CustomerCreditProfileRequestStatus.PENDING)
                .build();
    }

    private SubmitCustomerCreditProfileRequest submit(String limit, int term) {
        SubmitCustomerCreditProfileRequest request = new SubmitCustomerCreditProfileRequest();
        request.setCreditLimit(new BigDecimal(limit));
        request.setCreditTermDays(term);
        request.setReason("Khách đề nghị mua chịu.");
        return request;
    }

    private CustomerCreditProfileRequestResponse responseFor(CustomerCreditProfileRequest request) {
        return CustomerCreditProfileRequestResponse.builder()
                .requestId(request.getRequestId())
                .customerId(request.getCustomerId())
                .requesterId(request.getRequesterId())
                .requestedCreditLimit(request.getRequestedCreditLimit())
                .requestedCreditTermDays(request.getRequestedCreditTermDays())
                .status(request.getStatus())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .reviewNote(request.getReviewNote())
                .createdAt(request.getCreatedAt())
                .identityEvidenceProvided(request.getIdentityDocumentNumber() != null
                        && request.getResidentialAddress() != null
                        && request.getWorksiteAddress() != null)
                .build();
    }

    private UserSummaryDto user(UUID id, String role) {
        return UserSummaryDto.builder().id(id).roles(List.of(role)).build();
    }
}
