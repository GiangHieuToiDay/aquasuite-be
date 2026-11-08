package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.api.dto.CreditApprovalGrantDto;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.AuthProvider;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.CreditApprovalErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.CreditApprovalGrantServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditApprovalGrantServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CreditApprovalGrantStore grantStore;

    @Test
    void issueAuthenticatesActiveShopOwnerAndBindsEveryApprovedValue() {
        UUID requesterId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        User owner = owner(ownerId);
        when(userRepository.findWithRolesAndPermissionsByEmailIgnoreCase("owner@example.com")).thenReturn(Optional.of(owner));
        when(passwordEncoder.matches("correct-password", owner.getPasswordHash())).thenReturn(true);
        CreditApprovalGrantServiceImpl service = new CreditApprovalGrantServiceImpl(userRepository, passwordEncoder, grantStore);

        CreateCreditApprovalGrantRequest request = new CreateCreditApprovalGrantRequest();
        request.setOwnerEmail("owner@example.com");
        request.setPassword("correct-password");
        request.setCustomerId(customerId);
        request.setCreditLimit(new BigDecimal("20000000"));
        request.setCreditTermDays(15);

        var response = service.issue(requesterId, request);

        ArgumentCaptor<CreditApprovalGrantStore.GrantData> grant = ArgumentCaptor.forClass(CreditApprovalGrantStore.GrantData.class);
        verify(grantStore).put(eq(response.approvalToken()), grant.capture(), any());
        assertEquals(ownerId, response.approvedBy());
        assertEquals(requesterId, grant.getValue().requesterId());
        assertEquals(customerId, grant.getValue().customerId());
        assertEquals(new BigDecimal("20000000"), grant.getValue().creditLimit());
        assertEquals(15, grant.getValue().creditTermDays());
    }

    @Test
    void consumeRejectsGrantBoundToDifferentCreditLimit() {
        UUID requesterId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        when(grantStore.consume("one-time-token")).thenReturn(new CreditApprovalGrantStore.GrantData(
                UUID.randomUUID(), requesterId, customerId, new BigDecimal("20000000"), 15, Instant.now().plusSeconds(30)));
        CreditApprovalGrantServiceImpl service = new CreditApprovalGrantServiceImpl(userRepository, passwordEncoder, grantStore);

        AppException exception = assertThrows(AppException.class, () -> service.consumeCreditProfileGrant(
                requesterId, "one-time-token", customerId, new BigDecimal("20000001"), 15));

        assertEquals(CreditApprovalErrorCode.INVALID_GRANT, exception.getErrorCode());
    }

    private User owner(UUID ownerId) {
        User user = new User();
        user.setId(ownerId);
        user.setEmail("owner@example.com");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setProvider(AuthProvider.LOCAL);
        Role role = new Role();
        role.setRoleName("SHOP_OWNER");
        user.setRoles(Set.of(role));
        return user;
    }
}
