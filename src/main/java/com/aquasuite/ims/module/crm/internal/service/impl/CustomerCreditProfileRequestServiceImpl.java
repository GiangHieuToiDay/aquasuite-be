package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileRequestEvent;
import com.aquasuite.ims.module.crm.api.event.CustomerCreditProfileUpdatedEvent;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.enums.CustomerCreditProfileRequestStatus;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.mapper.CustomerCreditProfileRequestMapper;
import com.aquasuite.ims.module.crm.internal.payload.request.ApproveCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.RejectCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestDetailResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerCreditProfileRequestRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileRequestService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.sales.api.CustomerOrderHistoryApi;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomerCreditProfileRequestServiceImpl implements CustomerCreditProfileRequestService {
    static final BigDecimal MAX_CREDIT_LIMIT = new BigDecimal("500000000.00");
    static final BigDecimal RETAIL_SELF_APPROVAL_LIMIT = new BigDecimal("2000000.00");
    static final BigDecimal CONTRACTOR_SELF_APPROVAL_LIMIT = new BigDecimal("10000000.00");
    static final long REQUIRED_QUALIFIED_ORDER_COUNT = 3L;

    private final CustomerRepository customerRepository;
    private final CustomerCreditProfileRequestRepository requestRepository;
    private final CustomerCreditProfileRequestMapper requestMapper;
    private final CustomerOrderHistoryApi customerOrderHistoryApi;
    private final IamApi iamApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional
    public CustomerCreditProfileRequestResponse submit(UUID customerId, SubmitCustomerCreditProfileRequest input) {
        validateProfile(input);
        UserSummaryDto currentUser = iamApi.getCurrentUser();
        Customer customer = findActiveCustomerForUpdate(customerId);
        long qualifiedOrderCount = customerOrderHistoryApi.countQualifiedOrders(customerId);
        boolean evidenceProvided = evidenceProvided(input);

        boolean applyImmediately = isShopOwner(currentUser)
                || isSalesEligible(customer.getCustomerType(), qualifiedOrderCount, input.getCreditLimit(), evidenceProvided);
        if (requestRepository.existsByCustomerIdAndStatus(customerId, CustomerCreditProfileRequestStatus.PENDING)) {
            throw new AppException(CrmErrorCode.CREDIT_PROFILE_REQUEST_ALREADY_PENDING);
        }

        Instant now = Instant.now();
        CustomerCreditProfileRequest request = CustomerCreditProfileRequest.builder()
                .customerId(customerId)
                .requesterId(currentUser.id())
                .requestedCreditLimit(input.getCreditLimit())
                .requestedCreditTermDays(input.getCreditTermDays())
                .reason(trim(input.getReason()))
                .identityDocumentNumber(trim(input.getIdentityDocumentNumber()))
                .residentialAddress(trim(input.getResidentialAddress()))
                .worksiteAddress(trim(input.getWorksiteAddress()))
                .status(applyImmediately ? CustomerCreditProfileRequestStatus.APPROVED : CustomerCreditProfileRequestStatus.PENDING)
                .reviewedBy(applyImmediately ? currentUser.id() : null)
                .reviewedAt(applyImmediately ? now : null)
                .reviewNote(applyImmediately ? (isShopOwner(currentUser) ? "Applied by Shop Owner." : "Applied by automatic credit policy.") : null)
                .build();
        try {
            request = applyImmediately ? requestRepository.save(request) : requestRepository.saveAndFlush(request);
        } catch (DataIntegrityViolationException exception) {
            throw new AppException(CrmErrorCode.CREDIT_PROFILE_REQUEST_ALREADY_PENDING);
        }

        if (applyImmediately) {
            applyActiveProfile(customer, request, currentUser.id(), "Credit profile approved by policy.");
        }
        publishRequestEvent(request, currentUser.id(), applyImmediately ? "AUTO_APPLIED" : "SUBMITTED", now);
        return response(request, applyImmediately, qualifiedOrderCount);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<CustomerCreditProfileRequestResponse> list(CustomerCreditProfileRequestStatus status, Pageable pageable) {
        CustomerCreditProfileRequestStatus resolvedStatus = status == null ? CustomerCreditProfileRequestStatus.PENDING : status;
        return requestRepository.findByStatus(resolvedStatus, pageable)
                .map(request -> response(request, false, customerOrderHistoryApi.countQualifiedOrders(request.getCustomerId())));
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerCreditProfileRequestDetailResponse detail(UUID requestId) {
        CustomerCreditProfileRequest request = findRequest(requestId);
        return CustomerCreditProfileRequestDetailResponse.builder()
                .requestId(request.getRequestId())
                .customerId(request.getCustomerId())
                .requesterId(request.getRequesterId())
                .requestedCreditLimit(request.getRequestedCreditLimit())
                .requestedCreditTermDays(request.getRequestedCreditTermDays())
                .reason(request.getReason())
                .identityDocumentNumber(request.getIdentityDocumentNumber())
                .residentialAddress(request.getResidentialAddress())
                .worksiteAddress(request.getWorksiteAddress())
                .status(request.getStatus())
                .reviewedBy(request.getReviewedBy())
                .reviewedAt(request.getReviewedAt())
                .reviewNote(request.getReviewNote())
                .createdAt(request.getCreatedAt())
                .updatedAt(request.getUpdatedAt())
                .qualifiedOrderCount(customerOrderHistoryApi.countQualifiedOrders(request.getCustomerId()))
                .build();
    }

    @Override
    @Transactional
    public CustomerCreditProfileRequestResponse approve(UUID requestId, ApproveCustomerCreditProfileRequest input) {
        UUID ownerId = requireShopOwner().id();
        CustomerCreditProfileRequest request = findPendingRequestForUpdate(requestId);
        Customer customer = findActiveCustomerForUpdate(request.getCustomerId());
        request.setStatus(CustomerCreditProfileRequestStatus.APPROVED);
        request.setReviewedBy(ownerId);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trim(input.getReviewNote()));
        requestRepository.save(request);
        applyActiveProfile(customer, request, ownerId, "Credit profile approved by Shop Owner.");
        publishRequestEvent(request, ownerId, "APPROVED", request.getReviewedAt());
        return response(request, true, customerOrderHistoryApi.countQualifiedOrders(request.getCustomerId()));
    }

    @Override
    @Transactional
    public CustomerCreditProfileRequestResponse reject(UUID requestId, RejectCustomerCreditProfileRequest input) {
        UUID ownerId = requireShopOwner().id();
        CustomerCreditProfileRequest request = findPendingRequestForUpdate(requestId);
        request.setStatus(CustomerCreditProfileRequestStatus.REJECTED);
        request.setReviewedBy(ownerId);
        request.setReviewedAt(Instant.now());
        request.setReviewNote(trim(input.getReviewNote()));
        requestRepository.save(request);
        publishRequestEvent(request, ownerId, "REJECTED", request.getReviewedAt());
        return response(request, false, customerOrderHistoryApi.countQualifiedOrders(request.getCustomerId()));
    }

    private Customer findActiveCustomerForUpdate(UUID customerId) {
        Customer customer = customerRepository.findByIdForUpdate(customerId)
                .orElseThrow(() -> new AppException(CrmErrorCode.CUSTOMER_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new AppException(CrmErrorCode.CUSTOMER_NOT_ACTIVE_FOR_CREDIT);
        }
        return customer;
    }

    private CustomerCreditProfileRequest findRequest(UUID requestId) {
        return requestRepository.findById(requestId)
                .orElseThrow(() -> new AppException(CrmErrorCode.CREDIT_PROFILE_REQUEST_NOT_FOUND));
    }

    private CustomerCreditProfileRequest findPendingRequestForUpdate(UUID requestId) {
        CustomerCreditProfileRequest request = requestRepository.findByIdForUpdate(requestId)
                .orElseThrow(() -> new AppException(CrmErrorCode.CREDIT_PROFILE_REQUEST_NOT_FOUND));
        if (request.getStatus() != CustomerCreditProfileRequestStatus.PENDING) {
            throw new AppException(CrmErrorCode.CREDIT_PROFILE_REQUEST_NOT_PENDING);
        }
        return request;
    }

    private void applyActiveProfile(Customer customer, CustomerCreditProfileRequest request, UUID approverId, String auditReason) {
        BigDecimal oldLimit = customer.getCreditLimit() == null ? BigDecimal.ZERO : customer.getCreditLimit();
        Integer oldTerm = customer.getCreditTermDays();
        customer.setCreditLimit(request.getRequestedCreditLimit());
        customer.setCreditTermDays(request.getRequestedCreditTermDays());
        customerRepository.save(customer);
        eventPublisher.publishEvent(new CustomerCreditProfileUpdatedEvent(UUID.randomUUID(), customer.getCustomerId(),
                request.getRequesterId(), approverId, oldLimit, oldTerm, request.getRequestedCreditLimit(),
                request.getRequestedCreditTermDays(), auditReason, Instant.now()));
    }

    private void publishRequestEvent(CustomerCreditProfileRequest request, UUID actorId, String action, Instant occurredAt) {
        eventPublisher.publishEvent(new CustomerCreditProfileRequestEvent(UUID.randomUUID(), request.getRequestId(),
                request.getCustomerId(), actorId, action, request.getRequestedCreditLimit(),
                request.getRequestedCreditTermDays(), evidenceProvided(request), occurredAt));
    }

    private CustomerCreditProfileRequestResponse response(CustomerCreditProfileRequest request, boolean appliedImmediately,
                                                           long qualifiedOrderCount) {
        CustomerCreditProfileRequestResponse mapped = requestMapper.toResponse(request);
        return new CustomerCreditProfileRequestResponse(mapped.requestId(), mapped.customerId(), mapped.requesterId(),
                mapped.requestedCreditLimit(), mapped.requestedCreditTermDays(), mapped.status(), mapped.reviewedBy(),
                mapped.reviewedAt(), mapped.reviewNote(), mapped.createdAt(), appliedImmediately,
                qualifiedOrderCount, mapped.identityEvidenceProvided());
    }

    private void validateProfile(SubmitCustomerCreditProfileRequest input) {
        if (input.getCreditLimit() == null || input.getCreditLimit().compareTo(BigDecimal.ZERO) <= 0
                || input.getCreditTermDays() == null || input.getCreditTermDays() < 1 || input.getCreditTermDays() > 30) {
            throw new AppException(CrmErrorCode.INVALID_CREDIT_PROFILE);
        }
        if (input.getCreditLimit().compareTo(MAX_CREDIT_LIMIT) > 0) {
            throw new AppException(CrmErrorCode.CREDIT_LIMIT_EXCEEDS_MAXIMUM);
        }
    }

    private boolean isSalesEligible(CustomerType customerType, long qualifiedOrders, BigDecimal requestedLimit,
                                    boolean evidencePresent) {
        if (qualifiedOrders < REQUIRED_QUALIFIED_ORDER_COUNT) return false;
        if (customerType == CustomerType.RETAIL) return requestedLimit.compareTo(RETAIL_SELF_APPROVAL_LIMIT) <= 0;
        return isContractorGroup(customerType) && evidencePresent
                && requestedLimit.compareTo(CONTRACTOR_SELF_APPROVAL_LIMIT) <= 0;
    }

    private boolean isContractorGroup(CustomerType type) {
        return type == CustomerType.INSTALLER || type == CustomerType.CONTRACTOR || type == CustomerType.PROJECT_CUSTOMER;
    }

    private boolean isShopOwner(UserSummaryDto user) {
        return user.roles() != null && user.roles().stream()
                .map(role -> role.toUpperCase(Locale.ROOT))
                .anyMatch(role -> role.equals("SHOP_OWNER") || role.equals("ROLE_SHOP_OWNER"));
    }

    private UserSummaryDto requireShopOwner() {
        UserSummaryDto currentUser = iamApi.getCurrentUser();
        if (!isShopOwner(currentUser)) {
            throw new org.springframework.security.access.AccessDeniedException("Shop Owner role is required.");
        }
        return currentUser;
    }

    private boolean evidenceProvided(SubmitCustomerCreditProfileRequest request) {
        return nonBlank(request.getIdentityDocumentNumber()) && nonBlank(request.getResidentialAddress()) && nonBlank(request.getWorksiteAddress());
    }

    private boolean evidenceProvided(CustomerCreditProfileRequest request) {
        return nonBlank(request.getIdentityDocumentNumber()) && nonBlank(request.getResidentialAddress()) && nonBlank(request.getWorksiteAddress());
    }

    private boolean nonBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String trim(String value) {
        return value == null ? null : value.trim();
    }
}
