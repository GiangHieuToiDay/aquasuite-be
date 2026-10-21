package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.api.IamFacade;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.share.exception.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import java.util.UUID;

/** Reads the UUID subject of the JWT authenticated by the shared Auth filter. No mock fallback. */
@Service
@RequiredArgsConstructor
public class IamFacadeImpl implements IamFacade {
    private final UserRepository users;

    @Override
    public UUID getCurrentUserId() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwt) || !jwt.isAuthenticated())
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        try {
            return UUID.fromString(jwt.getToken().getSubject());
        } catch (IllegalArgumentException | NullPointerException failure) {
            throw new AppException(CommonErrorCode.UNAUTHORIZED);
        }
    }

    @Override
    public UserSummaryDto getCurrentUser() {
        var user =
                users.findById(getCurrentUserId())
                        .orElseThrow(() -> new AppException(CommonErrorCode.UNAUTHORIZED));
        return new UserSummaryDto(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getPhone(),
                user.getAvatarUrl(),
                user.getStatus().name());
    }
}
