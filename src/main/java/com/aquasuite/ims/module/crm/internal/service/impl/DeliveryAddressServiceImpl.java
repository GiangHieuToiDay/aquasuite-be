package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.entity.CustomerAddress;
import com.aquasuite.ims.module.crm.internal.entity.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.exception.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.payload.req.CreateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerAddressRepository;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.DeliveryAddressService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryAddressServiceImpl implements DeliveryAddressService {

    private final CustomerRepository customerRepository;
    private final CustomerAddressRepository customerAddressRepository;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAddressResponse> getAddressesByUserId(UUID userId) {
        return customerRepository.findByUserId(userId)
                .map(customer -> customerAddressRepository
                        .findByCustomerIdAndIsDeletedFalseOrderByIsDefaultDescUpdatedAtDesc(customer.getCustomerId())
                        .stream()
                        .map(this::toResponse)
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAddressResponse getAddressDetail(UUID userId, UUID addressId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));
        CustomerAddress address = customerAddressRepository
                .findByAddressIdAndCustomerIdAndIsDeletedFalse(addressId, customer.getCustomerId())
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        return toResponse(address);
    }

    @Override
    @Transactional
    public DeliveryAddressResponse createAddress(UUID userId, CreateDeliveryAddressRequest request) {
        Customer customer = getOrCreateCustomer(userId);

        long activeCount = customerAddressRepository.countByCustomerIdAndIsDeletedFalse(customer.getCustomerId());
        boolean shouldBeDefault = activeCount == 0 || Boolean.TRUE.equals(request.isDefault());

        if (shouldBeDefault && activeCount > 0) {
            customerAddressRepository.clearDefaultAddresses(customer.getCustomerId());
        }

        String label = (request.label() != null && !request.label().isBlank())
                ? request.label().trim()
                : "Home";

        String addressText = buildAddressText(request.streetAddress(), request.ward(), request.district(), request.province());

        CustomerAddress newAddress = CustomerAddress.builder()
                .addressId(UUID.randomUUID())
                .customerId(customer.getCustomerId())
                .recipientName(request.recipientName().trim())
                .phone(request.phone().trim())
                .province(request.province().trim())
                .district(request.district().trim())
                .ward(request.ward().trim())
                .streetAddress(request.streetAddress().trim())
                .addressText(addressText)
                .label(label)
                .isDefault(shouldBeDefault)
                .isDeleted(false)
                .build();

        CustomerAddress saved = customerAddressRepository.save(newAddress);
        log.info("Created delivery address ID={} for customerId={}", saved.getAddressId(), customer.getCustomerId());
        return toResponse(saved);
    }

    @Override
    @Transactional
    public DeliveryAddressResponse updateAddress(UUID userId, UUID addressId, UpdateDeliveryAddressRequest request) {
        Customer customer = getOrCreateCustomer(userId);

        CustomerAddress address = customerAddressRepository
                .findByAddressIdAndIsDeletedFalse(addressId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        if (!address.getCustomerId().equals(customer.getCustomerId())) {
            log.warn("Access denied: User {} cannot update address {} owned by customer {}",
                    userId, addressId, address.getCustomerId());
            throw new AppException(CrmErrorCode.FORBIDDEN_ADDRESS_ACCESS);
        }

        boolean willBeDefault = Boolean.TRUE.equals(request.isDefault());
        if (willBeDefault && !address.isDefault()) {
            customerAddressRepository.clearDefaultAddresses(customer.getCustomerId());
            address.setDefault(true);
        } else if (request.isDefault() != null && !request.isDefault() && address.isDefault()) {
            // Không cho phép tự gỡ mặc định nếu đây là địa chỉ duy nhất
            long activeCount = customerAddressRepository.countByCustomerIdAndIsDeletedFalse(customer.getCustomerId());
            if (activeCount > 1) {
                address.setDefault(false);
            }
        }

        String label = (request.label() != null && !request.label().isBlank())
                ? request.label().trim()
                : address.getLabel();

        String addressText = buildAddressText(request.streetAddress(), request.ward(), request.district(), request.province());

        address.setRecipientName(request.recipientName().trim());
        address.setPhone(request.phone().trim());
        address.setProvince(request.province().trim());
        address.setDistrict(request.district().trim());
        address.setWard(request.ward().trim());
        address.setStreetAddress(request.streetAddress().trim());
        address.setAddressText(addressText);
        address.setLabel(label);

        CustomerAddress updated = customerAddressRepository.save(address);
        log.info("Updated delivery address ID={} for customerId={}", addressId, customer.getCustomerId());
        return toResponse(updated);
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Customer customer = getOrCreateCustomer(userId);

        CustomerAddress address = customerAddressRepository
                .findByAddressIdAndIsDeletedFalse(addressId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        if (!address.getCustomerId().equals(customer.getCustomerId())) {
            log.warn("Access denied: User {} cannot delete address {} owned by customer {}",
                    userId, addressId, address.getCustomerId());
            throw new AppException(CrmErrorCode.FORBIDDEN_ADDRESS_ACCESS);
        }

        boolean wasDefault = address.isDefault();
        address.setDeleted(true);
        address.setDefault(false);
        customerAddressRepository.save(address);
        log.info("Soft-deleted delivery address ID={} for customerId={}", addressId, customer.getCustomerId());

        // Nếu địa chỉ bị xóa là địa chỉ mặc định, tự động đôn địa chỉ còn lại lên làm mặc định
        if (wasDefault) {
            Optional<CustomerAddress> nextCandidate = customerAddressRepository
                    .findFirstByCustomerIdAndIsDeletedFalseOrderByUpdatedAtDesc(customer.getCustomerId());
            if (nextCandidate.isPresent()) {
                CustomerAddress newDefault = nextCandidate.get();
                newDefault.setDefault(true);
                customerAddressRepository.save(newDefault);
                log.info("Designated new default delivery address ID={}", newDefault.getAddressId());
            }
        }
    }

    @Override
    @Transactional
    public DeliveryAddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        Customer customer = getOrCreateCustomer(userId);

        CustomerAddress address = customerAddressRepository
                .findByAddressIdAndIsDeletedFalse(addressId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        if (!address.getCustomerId().equals(customer.getCustomerId())) {
            log.warn("Access denied: User {} cannot set default on address {} owned by customer {}",
                    userId, addressId, address.getCustomerId());
            throw new AppException(CrmErrorCode.FORBIDDEN_ADDRESS_ACCESS);
        }

        customerAddressRepository.clearDefaultAddresses(customer.getCustomerId());
        address.setDefault(true);
        CustomerAddress saved = customerAddressRepository.save(address);
        log.info("Set address ID={} as default for customerId={}", addressId, customer.getCustomerId());
        return toResponse(saved);
    }

    private Customer getOrCreateCustomer(UUID userId) {
        return customerRepository.findByUserId(userId).orElseGet(() -> {
            UserSummaryDto currentUser;
            try {
                currentUser = iamApi.getUserById(userId);
            } catch (Exception e) {
                currentUser = null;
            }

            String username = (currentUser != null && currentUser.fullName() != null && !currentUser.fullName().isBlank())
                    ? currentUser.fullName()
                    : (currentUser != null && currentUser.username() != null)
                    ? currentUser.username()
                    : "user_" + userId.toString().substring(0, 8);
            String phone = (currentUser != null && currentUser.phone() != null && !currentUser.phone().isBlank())
                    ? currentUser.phone()
                    : "0900000000";
            String email = currentUser != null ? currentUser.email() : null;

            Customer newCustomer = Customer.builder()
                    .customerId(UUID.randomUUID())
                    .userId(userId)
                    .customerCode(generateCustomerCode())
                    .customerName(username)
                    .phone(phone)
                    .email(email)
                    .customerType(CustomerType.RETAIL)
                    .status("ACTIVE")
                    .build();

            log.info("Auto-initialized Customer profile ID={} for userId={}", newCustomer.getCustomerId(), userId);
            return customerRepository.save(newCustomer);
        });
    }

    private DeliveryAddressResponse toResponse(CustomerAddress address) {
        return DeliveryAddressResponse.builder()
                .addressId(address.getAddressId())
                .customerId(address.getCustomerId())
                .recipientName(address.getRecipientName())
                .phone(address.getPhone())
                .province(address.getProvince())
                .district(address.getDistrict())
                .ward(address.getWard())
                .streetAddress(address.getStreetAddress())
                .addressText(address.getAddressText())
                .label(address.getLabel())
                .isDefault(address.isDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    private String buildAddressText(String street, String ward, String district, String province) {
        return String.format("%s, %s, %s, %s",
                street != null ? street.trim() : "",
                ward != null ? ward.trim() : "",
                district != null ? district.trim() : "",
                province != null ? province.trim() : "");
    }

    private String generateCustomerCode() {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMdd"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "CUS-" + ts + "-" + rand;
    }
}
