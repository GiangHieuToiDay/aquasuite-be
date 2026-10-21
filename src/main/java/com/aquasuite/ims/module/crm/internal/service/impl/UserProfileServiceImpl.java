package com.aquasuite.ims.module.crm.internal.service.impl;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.module.crm.internal.payload.req.UpdateProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.AvatarUploadResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.DeliveryAddressResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.UserProfileResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.CloudinaryStorageService;
import com.aquasuite.ims.module.crm.internal.service.DeliveryAddressService;
import com.aquasuite.ims.module.crm.internal.service.UserProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserProfileServiceImpl implements UserProfileService {

    private final CustomerRepository customerRepository;
    private final DeliveryAddressService deliveryAddressService;
    private final CloudinaryStorageService cloudinaryStorageService;
    private final IamApi iamApi;

    @Override
    @Transactional(readOnly = true)
    public UserProfileResponse getProfile(UUID userId) {
        UserSummaryDto user = iamApi.getUserById(userId);

        Optional<Customer> customerOpt = customerRepository.findByUserId(userId);
        String customerCode = customerOpt.map(Customer::getCustomerCode).orElse(null);
        String customerTier = customerOpt.map(c -> c.getCustomerType() != null ? c.getCustomerType().name() : "RETAIL").orElse(null);

        List<DeliveryAddressResponse> addresses = deliveryAddressService.getAddressesByUserId(userId);

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
}
