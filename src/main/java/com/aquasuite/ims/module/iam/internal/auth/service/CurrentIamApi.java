package com.aquasuite.ims.module.iam.internal.auth.service;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class CurrentIamApi implements IamApi {

    private final UserRepository userRepository;

    public CurrentIamApi(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserSummaryDto getCurrentUser() {
        User user = userRepository.findById(getCurrentUserId())
                .orElseThrow(() -> new AppException(CommonErrorCode.UNAUTHORIZED));
        return new UserSummaryDto(
                user.getId(), user.getUsername(), user.getEmail(), user.getPhone(),
                user.getAvatarUrl(), user.getStatus().name());
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
    public boolean isAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ADMIN")
                        || a.getAuthority().equalsIgnoreCase("SCOPE_ADMIN"));
    }

    @Override
    public boolean isSalesStaffOrAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return false;
        }
        return auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN")
                        || a.getAuthority().equals("ROLE_SUPER_ADMIN")
                        || a.getAuthority().equals("ADMIN")
                        || a.getAuthority().equals("ROLE_SALES_STAFF")
                        || a.getAuthority().equals("ROLE_STAFF")
                        || a.getAuthority().equals("SALES_STAFF"));
    }

    @Override
    public void validateAdminAccess() {
        if (!isAdmin()) {
            throw new AppException(CommonErrorCode.ACCESS_DENIED, "Chỉ tài khoản Quản trị viên (Admin) mới có quyền thực hiện thao tác này");
        }
    }
}
