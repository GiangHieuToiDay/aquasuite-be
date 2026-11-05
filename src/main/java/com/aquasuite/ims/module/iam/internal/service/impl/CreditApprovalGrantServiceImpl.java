package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.api.CreditApprovalGrantApi;
import com.aquasuite.ims.module.iam.api.dto.CreditApprovalGrantDto;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.entity.AuthProvider;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.error.CreditApprovalErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.CreditApprovalGrantResponse;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.CreditApprovalGrantService;
import com.aquasuite.ims.module.iam.internal.service.CreditApprovalGrantStore;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditApprovalGrantServiceImpl implements CreditApprovalGrantService, CreditApprovalGrantApi {
    private static final Duration GRANT_TTL = Duration.ofMinutes(2);
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CreditApprovalGrantStore grantStore;
    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public CreditApprovalGrantResponse issue(UUID requesterId, CreateCreditApprovalGrantRequest request) {
        User owner = authenticateOwner(request.getOwnerEmail(), request.getPassword());
        Instant expiresAt = Instant.now().plus(GRANT_TTL);
        String token = token();
        grantStore.put(token, new CreditApprovalGrantStore.GrantData(owner.getId(), requesterId,
                request.getCustomerId(), request.getCreditLimit(), request.getCreditTermDays(), expiresAt), GRANT_TTL);
        return CreditApprovalGrantResponse.builder().approvalToken(token).expiresAt(expiresAt).approvedBy(owner.getId()).build();
    }

    @Override
    public CreditApprovalGrantDto consumeCreditProfileGrant(UUID requesterId, String grantToken, UUID customerId,
                                                             BigDecimal creditLimit, Integer creditTermDays) {
        CreditApprovalGrantStore.GrantData grant = grantStore.consume(grantToken);
        if (!requesterId.equals(grant.requesterId()) || !customerId.equals(grant.customerId())
                || creditLimit.compareTo(grant.creditLimit()) != 0 || !creditTermDays.equals(grant.creditTermDays())
                || Instant.now().isAfter(grant.expiresAt())) {
            throw new AppException(CreditApprovalErrorCode.INVALID_GRANT);
        }
        return new CreditApprovalGrantDto(grant.approverId(), grant.requesterId(), grant.customerId());
    }

    private User authenticateOwner(String ownerEmail, String password) {
        User owner = userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(ownerEmail.trim())
                .orElseThrow(() -> new AppException(AuthErrorCode.INVALID_CREDENTIALS));
        if (!passwordEncoder.matches(password, owner.getPasswordHash())) {
            throw new AppException(AuthErrorCode.INVALID_CREDENTIALS);
        }
        boolean ownerRole = owner.getRoles().stream().anyMatch(role -> {
            String roleName = role.getRoleName() == null ? "" : role.getRoleName().toUpperCase(Locale.ROOT);
            return "SHOP_OWNER".equals(roleName) || "ROLE_SHOP_OWNER".equals(roleName);
        });
        if (owner.getStatus() != UserStatus.ACTIVE || owner.getProvider() != AuthProvider.LOCAL) {
            throw new AppException(AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }
        if (!ownerRole) {
            throw new AppException(CreditApprovalErrorCode.APPROVER_NOT_OWNER);
        }
        return owner;
    }

    private String token() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
