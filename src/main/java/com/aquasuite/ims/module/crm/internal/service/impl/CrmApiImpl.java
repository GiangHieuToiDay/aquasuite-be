package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.DeliveryAddressItem;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmApiImpl implements CrmApi {

    private final CustomerRepository customerRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummaryDto> getCustomerByUserId(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return customerRepository.findByUserId(userId).map(this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummaryDto> getCustomerById(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return customerRepository.findById(customerId).map(this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerSummaryDto> getCustomerByPhone(String phone) {
        if (phone == null || phone.isBlank()) {
            return Optional.empty();
        }
        return customerRepository.findByPhone(phone.trim()).map(this::toSummaryDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressDto> getDeliveryAddressesByUserId(UUID userId) {
        if (userId == null) {
            return Collections.emptyList();
        }
        return customerRepository.findByUserId(userId)
                .map(customer -> getDeliveryAddressesByCustomerId(customer.getCustomerId()))
                .orElse(Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerAddressDto> getDeliveryAddressesByCustomerId(UUID customerId) {
        if (customerId == null) {
            return Collections.emptyList();
        }
        return customerRepository.findById(customerId)
                .map(customer -> customer.getDeliveryAddresses().stream()
                        .filter(a -> !a.isDeleted())
                        .sorted(Comparator.comparing(DeliveryAddressItem::isDefault).reversed()
                                .thenComparing(Comparator.comparing(DeliveryAddressItem::getUpdatedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder()))))
                        .map(a -> toAddressDto(a, customer.getCustomerId()))
                        .toList())
                .orElse(Collections.emptyList());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerAddressDto> getDefaultDeliveryAddress(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return customerRepository.findById(customerId)
                .flatMap(customer -> customer.getDeliveryAddresses().stream()
                        .filter(a -> !a.isDeleted() && a.isDefault())
                        .findFirst()
                        .map(a -> toAddressDto(a, customer.getCustomerId())));
    }

    @Override
    @Transactional
    public CustomerSummaryDto getOrCreateCounterCustomer(String phone, String name) {
        String canonicalPhone = phone == null ? "" : phone.trim();
        if (!canonicalPhone.matches("0[35789][0-9]{8}")) {
            throw new AppException(
                    CommonErrorCode.VALIDATION_ERROR);
        }
        String customerName = name == null || name.isBlank() ? "Customer " + canonicalPhone : name.trim();
        if (customerName.length() > 150) {
            throw new AppException(
                    CommonErrorCode.VALIDATION_ERROR);
        }
        UUID id = UUID.randomUUID();
        customerRepository.insertUnlinkedCustomer(id, "CUS-" + id, customerName, canonicalPhone, null);
        return customerRepository.findByPhone(canonicalPhone).map(this::toSummaryDto)
                .orElseThrow(() -> new AppException(
                        CommonErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    @Override
    @Transactional
    public CustomerSummaryDto getOrCreateWalkInCustomer() {
        customerRepository.insertUnlinkedCustomer(UUID.randomUUID(), "CUS-WALKIN",
                "Walk-in Retail Customer", "0000000000", null);
        return customerRepository.findByPhone("0000000000").map(this::toSummaryDto)
                .orElseThrow(() -> new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    @Override
    @Transactional(readOnly = true)
    public List<CustomerDebtProfileDto> findAllDebtProfiles() {
        return customerRepository.findAll().stream().map(this::toDebtProfileDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerDebtProfileDto> getDebtProfile(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return customerRepository.findById(customerId).map(this::toDebtProfileDto);
    }

    @Override
    @Transactional
    public void updateCustomerStatus(UUID customerId, String status) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new IllegalArgumentException("Customer not found: " + customerId));
        customer.setStatus(status);
        customerRepository.save(customer);
    }

    private CustomerSummaryDto toSummaryDto(Customer c) {
        return CustomerSummaryDto.builder()
                .customerId(c.getCustomerId())
                .userId(c.getUserId())
                .customerCode(c.getCustomerCode())
                .customerName(c.getCustomerName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .customerType(c.getCustomerType() != null ? c.getCustomerType().name() : null)
                .companyName(c.getCompanyName())
                .status(c.getStatus())
                .build();
    }

    private CustomerAddressDto toAddressDto(DeliveryAddressItem a, UUID customerId) {
        return CustomerAddressDto.builder()
                .addressId(a.getAddressId())
                .customerId(customerId)
                .recipientName(a.getRecipientName())
                .phone(a.getPhone())
                .province(a.getProvince())
                .district(a.getDistrict())
                .ward(a.getWard())
                .streetAddress(a.getStreetAddress())
                .addressText(a.getAddressText())
                .label(a.getLabel())
                .isDefault(a.isDefault())
                .createdAt(a.getCreatedAt())
                .updatedAt(a.getUpdatedAt())
                .build();
    }

    private CustomerDebtProfileDto toDebtProfileDto(Customer customer) {
        List<String> addresses = customer.getAddress() == null
                ? List.of()
                : Arrays.stream(customer.getAddress()).filter(value -> value != null && !value.isBlank()).toList();
        List<String> projects = customer.getProjects() == null
                ? List.of()
                : Arrays.stream(customer.getProjects()).filter(value -> value != null && !value.isBlank()).toList();
        return new CustomerDebtProfileDto(
                customer.getCustomerId(), customer.getCustomerCode(), customer.getCustomerName(),
                customer.getPhone(), customer.getEmail(),
                customer.getCustomerType() == null ? null : customer.getCustomerType().name(),
                customer.getTaxCode(), customer.getCompanyName(), addresses, projects,
                customer.getCreditLimit(), customer.getCreditTermDays(), customer.getStatus());
    }
}
