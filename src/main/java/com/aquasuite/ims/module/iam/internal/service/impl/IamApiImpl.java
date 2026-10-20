package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.security.SecurityUtils;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class IamApiImpl implements IamApi {

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

    @Override
    public boolean isAdmin() {
        return SecurityUtils.isCurrentUserAdmin();
    }

    @Override
    public void validateAdminAccess() {
        if (!isAdmin()) {
            log.warn("Access denied: User ID {} is not an Admin", getCurrentUserId());
            throw new AppException(CommonErrorCode.ACCESS_DENIED, "Chỉ tài khoản Quản trị viên (Admin) mới có quyền thực hiện thao tác này");
        }
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
