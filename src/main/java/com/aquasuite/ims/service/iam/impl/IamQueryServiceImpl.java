package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.service.iam.IamQueryService;


import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class IamQueryServiceImpl implements IamQueryService {

    private final UserRepository userRepository;

    public IamQueryServiceImpl(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser() {
        return getUserById(getCurrentUserId());
    }

    @Override
    public UUID getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }
        try {
            return UUID.fromString(jwtAuthentication.getToken().getSubject());
        } catch (IllegalArgumentException ex) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getUserById(UUID userId) {
        User user = userRepository.findWithRolesAndPermissionsById(userId)
                .orElseThrow(() -> new AppException(CommonErrorCode.UNAUTHORIZED));

        return toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public java.util.Optional<UserSummaryDto> findByEmail(String email) {
        if (email == null || email.isBlank()) {
            return java.util.Optional.empty();
        }
        return userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(email.trim())
                .map(this::toDto);
    }

    private UserSummaryDto toDto(User user) {
        java.util.List<String> roleNames = user.getRoles() != null
                ? user.getRoles().stream()
                .map(r -> r.getRoleName() != null ? r.getRoleName().trim() : "")
                .filter(name -> !name.isEmpty())
                .toList()
                : java.util.List.of();

        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .fullName(user.getFullName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatarUrl(user.getAvatarUrl())
                .status(user.getStatus() != null ? user.getStatus().name() : "ACTIVE")
                .roles(roleNames)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByPhoneAndUserIdNot(String phone, UUID userId) {
        if (phone == null || phone.isBlank()) {
            return false;
        }
        return userRepository.existsByPhoneAndIdNot(phone.trim(), userId);
    }

    @Override
    @Transactional
    public void updateUserProfile(UUID userId, String fullName, String phone, String avatarUrl) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(CommonErrorCode.UNAUTHORIZED));

        if (fullName != null) {
            user.setFullName(fullName.trim());
        }
        if (phone != null && !phone.isBlank()) {
            user.setPhone(phone.trim());
        }
        if (avatarUrl != null) {
            user.setAvatarUrl(avatarUrl.trim());
        }
        userRepository.save(user);
    }
}
