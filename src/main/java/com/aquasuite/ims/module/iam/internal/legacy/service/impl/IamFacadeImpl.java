package com.aquasuite.ims.module.iam.internal.legacy.service.impl;

import com.aquasuite.ims.module.iam.internal.legacy.IamFacade;
import com.aquasuite.ims.module.iam.internal.legacy.dto.UserSummaryDto;
import com.aquasuite.ims.module.iam.internal.legacy.entity.User;
import com.aquasuite.ims.module.iam.internal.legacy.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.legacy.security.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {

    private final UserRepository userRepository;

    @Override
    public UserSummaryDto getCurrentUser() {
        Long userId = getCurrentUserId();

        return userRepository.findById(userId)
                .map(this::toDto)
                .orElseGet(() -> {
                    log.warn("User with id {} not found in DB. Falling back to mock UserSummaryDto", userId);
                    return UserSummaryDto.builder()
                            .id(userId)
                            .username("hieuganrt")
                            .email("ganrt.hieu.08@gmail.com")
                            .phone("0987654321")
                            .avatar(null)
                            .status("ACTIVE")
                            .build();
                });
    }

    @Override
    public Long getCurrentUserId() {
        Long currentUserId = SecurityUtils.getCurrentUserId();
        return currentUserId != null ? currentUserId : 1L;
    }

    private UserSummaryDto toDto(User user) {
        return UserSummaryDto.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .phone(user.getPhone())
                .avatar(user.getAvatar())
                .status(user.getStatus() != null ? user.getStatus().name() : "ACTIVE")
                .build();
    }
}
