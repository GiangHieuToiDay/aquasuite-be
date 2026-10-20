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
}
