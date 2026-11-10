package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.DeliveryAddressItem;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.payload.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
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
    private final CustomerCreditProfileRequestService creditProfileRequestService;

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
    public CustomerSummaryDto getOrCreateWalkInCustomer() {
        return getOrCreateWalkInCustomer("Khách lẻ tại quầy", null);
    }

    @Override
    @Transactional
    public CustomerSummaryDto getOrCreateWalkInCustomer(String customerName, String phone) {
        String cleanPhone = (phone != null && !phone.isBlank() && !"0000000000".equals(phone.trim())) ? phone.trim() : null;
        String cleanName = (customerName != null && !customerName.isBlank() && !"Walk-in Retail Customer".equalsIgnoreCase(customerName.trim()))
                ? customerName.trim()
                : "Khách lẻ tại quầy";

        // Nếu không có SĐT:
        if (cleanPhone == null) {
            // Nếu có tên riêng cụ thể (khác "Khách lẻ tại quầy"): lưu thành khách hàng mới để lần sau tái sử dụng
            if (!"Khách lẻ tại quầy".equals(cleanName)) {
                String code = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                Customer newCus = Customer.builder()
                        .customerId(UUID.randomUUID())
                        .customerCode(code)
                        .customerName(cleanName)
                        .phone("")
                        .customerType(CustomerType.RETAIL)
                        .status("ACTIVE")
                        .build();
                return toSummaryDto(customerRepository.save(newCus));
            }

            // Nếu không có tên riêng hoặc là "Khách lẻ tại quầy": dùng bản ghi mặc định CUS-WALKIN
            return customerRepository.findByCustomerCode("CUS-WALKIN")
                    .map(this::toSummaryDto)
                    .orElseGet(() -> {
                        Customer walkIn = Customer.builder()
                                .customerId(UUID.randomUUID())
                                .customerCode("CUS-WALKIN")
                                .customerName("Khách lẻ tại quầy")
                                .phone("")
                                .customerType(CustomerType.RETAIL)
                                .status("ACTIVE")
                                .build();
                        return toSummaryDto(customerRepository.save(walkIn));
                    });
        }

        // Nếu có SĐT: tìm theo SĐT
        return customerRepository.findByPhone(cleanPhone)
                .map(existing -> {
                    if (!"Khách lẻ tại quầy".equals(cleanName) &&
                            ("Khách lẻ tại quầy".equals(existing.getCustomerName()) || existing.getCustomerName().isBlank())) {
                        existing.setCustomerName(cleanName);
                        return toSummaryDto(customerRepository.save(existing));
                    }
                    return toSummaryDto(existing);
                })
                .orElseGet(() -> {
                    String code = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
                    Customer walkIn = Customer.builder()
                            .customerId(UUID.randomUUID())
                            .customerCode(code)
                            .customerName(cleanName)
                            .phone(cleanPhone)
                            .customerType(CustomerType.RETAIL)
                            .status("ACTIVE")
                            .build();
                    return toSummaryDto(customerRepository.save(walkIn));
                });
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
        String name = c.getCustomerName();
        if (name == null || name.isBlank() || "Walk-in Retail Customer".equalsIgnoreCase(name.trim())) {
            name = "Khách lẻ tại quầy";
        }
        String phone = c.getPhone();
        if (phone == null || phone.isBlank() || "0000000000".equals(phone.trim())) {
            phone = null;
        }

        return CustomerSummaryDto.builder()
                .customerId(c.getCustomerId())
                .userId(c.getUserId())
                .customerCode(c.getCustomerCode())
                .customerName(name)
                .phone(phone)
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
        String name = customer.getCustomerName();
        if (name == null || name.isBlank() || "Walk-in Retail Customer".equalsIgnoreCase(name.trim())) {
            name = "Khách lẻ tại quầy";
        }
        String phone = customer.getPhone();
        if (phone == null || phone.isBlank() || "0000000000".equals(phone.trim())) {
            phone = null;
        }
        return new CustomerDebtProfileDto(
                customer.getCustomerId(), customer.getCustomerCode(), name,
                phone, customer.getEmail(),
                customer.getCustomerType() == null ? null : customer.getCustomerType().name(),
                customer.getTaxCode(), customer.getCompanyName(), addresses, projects,
                customer.getCreditLimit(), customer.getCreditTermDays(), customer.getStatus());
    }

    @Override
    @Transactional
    public UUID submitCreditProfileRequestFromQuotation(
            UUID customerId,
            BigDecimal requestedLimit,
            Integer creditTermDays,
            String reason,
            UUID requesterId) {
        SubmitCustomerCreditProfileRequest req = new SubmitCustomerCreditProfileRequest();
        req.setCreditLimit(requestedLimit);
        req.setCreditTermDays(creditTermDays != null ? creditTermDays : 30);
        req.setReason(reason != null && !reason.isBlank() ? reason.trim() : "Yêu cầu cấp/tăng hạn mức cho đơn hàng dự án.");
        var response = creditProfileRequestService.submit(customerId, req);
        return response.requestId();
    }
}
