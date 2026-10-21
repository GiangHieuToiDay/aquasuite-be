package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerAddress;
import com.aquasuite.ims.module.crm.internal.repository.CustomerAddressRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CrmApiImpl implements CrmApi {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;

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
        return customerAddressRepository
                .findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescUpdatedAtDesc(customerId)
                .stream()
                .map(this::toAddressDto)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CustomerAddressDto> getDefaultDeliveryAddress(UUID customerId) {
        if (customerId == null) {
            return Optional.empty();
        }
        return customerAddressRepository
                .findByCustomerIdAndIsDefaultTrueAndIsDeletedFalse(customerId)
                .map(this::toAddressDto);
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

    private CustomerAddressDto toAddressDto(CustomerAddress a) {
        return CustomerAddressDto.builder()
                .addressId(a.getAddressId())
                .customerId(a.getCustomerId())
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
}
