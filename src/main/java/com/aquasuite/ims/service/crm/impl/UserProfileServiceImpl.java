package com.aquasuite.ims.service.crm.impl;

import com.aquasuite.ims.dto.crm.request.CreateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateDeliveryAddressRequest;
import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.crm.response.AvatarUploadResponse;
import com.aquasuite.ims.dto.crm.response.DeliveryAddressResponse;
import com.aquasuite.ims.dto.crm.response.UserProfileResponse;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.entity.crm.Customer;
import com.aquasuite.ims.entity.crm.DeliveryAddressItem;
import com.aquasuite.ims.enums.crm.CustomerType;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.crm.CrmErrorCode;
import com.aquasuite.ims.integration.crm.CloudinaryStorageService;
import com.aquasuite.ims.repository.crm.CustomerRepository;
import com.aquasuite.ims.service.crm.UserProfileService;
import com.aquasuite.ims.service.iam.IamQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final CustomerRepository customerRepository;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final IamQueryService iamApi;

    // ── Profile ──────────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserSummaryDto user = iamApi.getUserById(userId);

        Optional<Customer> customerOpt = customerRepository.findByUserId(userId);
        String customerCode = customerOpt.map(Customer::getCustomerCode).orElse(null);
        String customerTier = customerOpt.map(c -> c.getCustomerType() != null ? c.getCustomerType().name() : "RETAIL").orElse(null);

        List<DeliveryAddressResponse> addresses = getAddressesByUserId(userId);

        return UserProfileResponse.builder()
                .id(user.id())
                .username(user.username())
                .fullName(user.fullName())
                .email(user.email())
                .phone(user.phone())
                .avatarUrl(user.avatarUrl())
                .roles(user.roles() != null ? user.roles() : List.of())
                .customerCode(customerCode)
                .customerTier(customerTier)
                .deliveryAddresses(addresses)
                .build();
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        log.info("Updating profile for user ID={}", userId);

        if (request.phone() != null && !request.phone().isBlank()) {
            boolean phoneExists = iamApi.existsByPhoneAndUserIdNot(request.phone().trim(), userId);
            if (phoneExists) {
                log.warn("Update profile rejected: phone {} already registered for another account", request.phone());
                throw new AppException(CrmErrorCode.DUPLICATE_PHONE);
            }
        }

        iamApi.updateUserProfile(userId, request.fullName(), request.phone(), request.avatarUrl());

        customerRepository.findByUserId(userId).ifPresent(customer -> {
            if (request.fullName() != null && !request.fullName().isBlank()) {
                customer.setCustomerName(request.fullName().trim());
            }
            if (request.phone() != null && !request.phone().isBlank()) {
                customer.setPhone(request.phone().trim());
            }
            customerRepository.save(customer);
            log.info("Synchronized customer record for user ID={}", userId);
        });

        return getProfile(userId);
    }

    @Override
    @Transactional
    public AvatarUploadResponse uploadAvatar(UUID userId, MultipartFile file) {
        log.info("Processing avatar upload for user ID={}", userId);
        String avatarUrl = cloudinaryStorageService.uploadAvatar(file);

        iamApi.updateUserProfile(userId, null, null, avatarUrl);

        return new AvatarUploadResponse(avatarUrl);
    }

    // ── Delivery Addresses (consolidated from DeliveryAddressServiceImpl) ───

    @Override
    @Transactional(readOnly = true)
    public List<DeliveryAddressResponse> getAddressesByUserId(UUID userId) {
        return customerRepository.findByUserId(userId)
                .map(customer -> customer.getDeliveryAddresses().stream()
                        .filter(a -> !a.isDeleted())
                        .sorted(Comparator.comparing(DeliveryAddressItem::isDefault).reversed()
                                .thenComparing(Comparator.comparing(DeliveryAddressItem::getUpdatedAt,
                                        Comparator.nullsLast(Comparator.reverseOrder()))))
                        .map(a -> toAddressResponse(a, customer.getCustomerId()))
                        .toList())
                .orElseGet(List::of);
    }

    @Override
    @Transactional(readOnly = true)
    public DeliveryAddressResponse getAddressDetail(UUID userId, UUID addressId) {
        Customer customer = customerRepository.findByUserId(userId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        DeliveryAddressItem address = findAddressInCustomer(customer, addressId)
                .orElseThrow(() -> new AppException(CrmErrorCode.ADDRESS_NOT_FOUND));

        return toAddressResponse(address, customer.getCustomerId());
    }

    @Override
    @Transactional
    public DeliveryAddressResponse createAddress(UUID userId, CreateDeliveryAddressRequest request) {
        Customer customer = getOrCreateCustomer(userId);

        List<DeliveryAddressItem> list = customer.getDeliveryAddresses();
        long activeCount = list.stream().filter(a -> !a.isDeleted()).count();
        boolean shouldBeDefault = activeCount == 0 || Boolean.TRUE.equals(request.isDefault());

        if (shouldBeDefault && activeCount > 0) {
            clearDefaultAddresses(customer);
        }

        String label = (request.label() != null && !request.label().isBlank())
                ? request.label().trim()
                : "Home";

        String addressText = buildAddressText(request.streetAddress(), request.ward(), request.district(), request.province());
        Instant now = Instant.now();

        DeliveryAddressItem newAddress = DeliveryAddressItem.builder()
                .addressId(UUID.randomUUID())
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
                .createdAt(now)
                .updatedAt(now)
                .build();

        list.add(newAddress);
        Customer savedCustomer = customerRepository.save(customer);
        log.info("Created delivery address ID={} in JSONB list for customerId={}", newAddress.getAddressId(), savedCustomer.getCustomerId());
        return toAddressResponse(newAddress, savedCustomer.getCustomerId());
    }

    @Override
    @Transactional
    public DeliveryAddressResponse updateAddress(UUID userId, UUID addressId, UpdateDeliveryAddressRequest request) {
        Customer customer = getOrCreateCustomer(userId);

        Optional<DeliveryAddressItem> targetOpt = findAddressInCustomer(customer, addressId);
        if (targetOpt.isEmpty()) {
            checkIfAddressBelongsToAnotherCustomer(addressId, customer.getCustomerId(), userId);
            throw new AppException(CrmErrorCode.ADDRESS_NOT_FOUND);
        }

        DeliveryAddressItem address = targetOpt.get();
        boolean willBeDefault = Boolean.TRUE.equals(request.isDefault());

        if (willBeDefault && !address.isDefault()) {
            clearDefaultAddresses(customer);
            address.setDefault(true);
        } else if (request.isDefault() != null && !request.isDefault() && address.isDefault()) {
            long activeCount = customer.getDeliveryAddresses().stream().filter(a -> !a.isDeleted()).count();
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
        address.setUpdatedAt(Instant.now());

        Customer savedCustomer = customerRepository.save(customer);
        log.info("Updated delivery address ID={} in JSONB list for customerId={}", addressId, savedCustomer.getCustomerId());
        return toAddressResponse(address, savedCustomer.getCustomerId());
    }

    @Override
    @Transactional
    public void deleteAddress(UUID userId, UUID addressId) {
        Customer customer = getOrCreateCustomer(userId);

        Optional<DeliveryAddressItem> targetOpt = findAddressInCustomer(customer, addressId);
        if (targetOpt.isEmpty()) {
            checkIfAddressBelongsToAnotherCustomer(addressId, customer.getCustomerId(), userId);
            throw new AppException(CrmErrorCode.ADDRESS_NOT_FOUND);
        }

        DeliveryAddressItem address = targetOpt.get();
        boolean wasDefault = address.isDefault();

        address.setDeleted(true);
        address.setDefault(false);
        address.setUpdatedAt(Instant.now());

        if (wasDefault) {
            Optional<DeliveryAddressItem> nextCandidate = customer.getDeliveryAddresses().stream()
                    .filter(a -> !a.isDeleted())
                    .max(Comparator.comparing(DeliveryAddressItem::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())));

            if (nextCandidate.isPresent()) {
                DeliveryAddressItem newDefault = nextCandidate.get();
                newDefault.setDefault(true);
                newDefault.setUpdatedAt(Instant.now());
                log.info("Designated new default delivery address ID={}", newDefault.getAddressId());
            }
        }

        customerRepository.save(customer);
        log.info("Soft-deleted delivery address ID={} in JSONB list for customerId={}", addressId, customer.getCustomerId());
    }

    @Override
    @Transactional
    public DeliveryAddressResponse setDefaultAddress(UUID userId, UUID addressId) {
        Customer customer = getOrCreateCustomer(userId);

        Optional<DeliveryAddressItem> targetOpt = findAddressInCustomer(customer, addressId);
        if (targetOpt.isEmpty()) {
            checkIfAddressBelongsToAnotherCustomer(addressId, customer.getCustomerId(), userId);
            throw new AppException(CrmErrorCode.ADDRESS_NOT_FOUND);
        }

        DeliveryAddressItem address = targetOpt.get();
        clearDefaultAddresses(customer);
        address.setDefault(true);
        address.setUpdatedAt(Instant.now());

        Customer saved = customerRepository.save(customer);
        log.info("Set address ID={} as default in JSONB list for customerId={}", addressId, saved.getCustomerId());
        return toAddressResponse(address, saved.getCustomerId());
    }

    private Optional<DeliveryAddressItem> findAddressInCustomer(Customer customer, UUID addressId) {
        return customer.getDeliveryAddresses().stream()
                .filter(a -> a.getAddressId() != null && a.getAddressId().equals(addressId) && !a.isDeleted())
                .findFirst();
    }

    private void clearDefaultAddresses(Customer customer) {
        customer.getDeliveryAddresses().forEach(a -> {
            if (a.isDefault()) {
                a.setDefault(false);
                a.setUpdatedAt(Instant.now());
            }
        });
    }

    private void checkIfAddressBelongsToAnotherCustomer(UUID addressId, UUID currentCustomerId, UUID userId) {
        List<Customer> allCustomers = customerRepository.findAll();
        for (Customer c : allCustomers) {
            if (!c.getCustomerId().equals(currentCustomerId)) {
                boolean foundInOther = c.getDeliveryAddresses().stream()
                        .anyMatch(a -> a.getAddressId() != null && a.getAddressId().equals(addressId) && !a.isDeleted());
                if (foundInOther) {
                    log.warn("Access denied: User {} cannot access address {} owned by customer {}",
                            userId, addressId, c.getCustomerId());
                    throw new AppException(CrmErrorCode.FORBIDDEN_ADDRESS_ACCESS);
                }
            }
        }
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

    private DeliveryAddressResponse toAddressResponse(DeliveryAddressItem address, UUID customerId) {
        return DeliveryAddressResponse.builder()
                .addressId(address.getAddressId())
                .customerId(customerId)
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
