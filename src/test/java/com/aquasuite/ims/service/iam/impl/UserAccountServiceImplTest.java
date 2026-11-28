package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.exception.iam.UserAccountErrorCode;
import com.aquasuite.ims.dto.iam.request.ChangeUserStatusRequest;
import com.aquasuite.ims.dto.iam.request.CreateUserAccountRequest;
import com.aquasuite.ims.dto.iam.request.UpdateUserAccountRequest;
import com.aquasuite.ims.dto.iam.response.UserAccountResponse;
import com.aquasuite.ims.mapper.iam.UserAccountMapper;
import com.aquasuite.ims.integration.iam.AccountCredentialsNotificationPort;
import com.aquasuite.ims.service.iam.impl.UserAccountServiceImpl;
import com.aquasuite.ims.event.iam.UserAccountCreatedEvent;
import com.aquasuite.ims.event.iam.UserAccountUpdatedEvent;
import com.aquasuite.ims.entity.iam.Role;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.repository.iam.RoleRepository;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.repository.iam.store.RedisSessionStore;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceImplTest {
    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private AccountCredentialsNotificationPort notificationPort;
    @Mock private RedisSessionStore sessionStore;
    @Mock private UserAccountMapper userAccountMapper;

    private UserAccountServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UserAccountServiceImpl(userRepository, roleRepository, new BCryptPasswordEncoder(),
                eventPublisher, notificationPort, sessionStore, userAccountMapper);
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void create_assignsAllSelectedRoles_withoutExposingTemporaryPassword() {
        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenAnswer(invocation -> {
            CreateUserAccountRequest request = invocation.getArgument(0);
            User user = new User();
            user.setUsername(request.getUsername().trim());
            user.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            user.setPhone(request.getPhone().trim());
            user.setAvatarUrl(request.getAvatarUrl());
            return user;
        });

        UUID actorId = UUID.randomUUID();
        Role staff = role("SALES_STAFF");
        Role accountant = role("ACCOUNTANT");
        when(roleRepository.findAllById(Set.of(staff.getRoleId(), accountant.getRoleId())))
                .thenReturn(List.of(staff, accountant));
        doAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.prePersist();
            return user;
        }).when(userRepository).saveAndFlush(any(User.class));
        when(userAccountMapper.toResponse(any(User.class), any(UUID.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return UserAccountResponse.builder()
                    .id(user.getId())
                    .status(user.getStatus().name())
                    .provider(user.getProvider().name())
                    .verified(user.isVerified())
                    .roles(user.getRoles().stream()
                            .map(role -> com.aquasuite.ims.dto.iam.response.RoleOptionResponse.builder()
                                    .id(role.getRoleId()).code(role.getRoleName()).description(role.getDescription()).build())
                            .toList())
                    .build();
        });

        UserAccountResponse response = service.create(CreateUserAccountRequest.builder()
                .username("staff.one").email("staff.one@example.com").phone("0901234567")
                .roleIds(Set.of(staff.getRoleId(), accountant.getRoleId()))
                .autoGenerateTemporaryPassword(false).temporaryPassword("ValidPassword@1")
                .confirmTemporaryPassword("ValidPassword@1").build(), actorId);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getProvider()).isEqualTo("LOCAL");
        assertThat(response.isVerified()).isFalse();
        assertThat(response.getRoles()).extracting(role -> role.getCode())
                .containsExactlyInAnyOrder("SALES_STAFF", "ACCOUNTANT");
        verify(userRepository).markRolesAssignedBy(response.getId(), actorId);
        ArgumentCaptor<UserAccountCreatedEvent> event = ArgumentCaptor.forClass(UserAccountCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().data()).doesNotContainKeys("password", "passwordHash", "temporaryPassword");
    }

    @Test
    void create_rejectsCustomerRoleOutsideRegistrationFlow() {
        Role customer = role("CUSTOMER");
        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenReturn(new User());
        when(roleRepository.findAllById(Set.of(customer.getRoleId()))).thenReturn(List.of(customer));

        assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                .username("customer.one").email("customer.one@example.com").phone("0901234567")
                .roleIds(Set.of(customer.getRoleId())).autoGenerateTemporaryPassword(true).build(), UUID.randomUUID()))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.CUSTOMER_ROLE_REQUIRES_REGISTRATION);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_rejectsAddingCustomerRoleToNonCustomerAccount() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Role staff = role("SALES_STAFF");
        Role customer = role("ROLE_CUSTOMER");
        when(userRepository.findWithRolesAndPermissionsById(targetId))
                .thenReturn(Optional.of(activeUser(targetId, staff)));
        when(roleRepository.findAllById(Set.of(customer.getRoleId()))).thenReturn(List.of(customer));

        assertThatThrownBy(() -> service.update(targetId, UpdateUserAccountRequest.builder()
                .username("staff").email("staff@example.com").phone("0901234567")
                .roleIds(Set.of(customer.getRoleId())).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.CUSTOMER_ROLE_REQUIRES_REGISTRATION);
        verify(userRepository, never()).saveAndFlush(any(User.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void update_allowsKeepingCustomerRoleOnExistingCustomerAccount() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Role customer = role("CUSTOMER");
        User target = activeUser(targetId, customer);
        when(userRepository.findWithRolesAndPermissionsById(targetId)).thenReturn(Optional.of(target));
        when(roleRepository.findAllById(Set.of(customer.getRoleId()))).thenReturn(List.of(customer));

        service.update(targetId, UpdateUserAccountRequest.builder()
                .username("customer").email("customer@example.com").phone("0901234567")
                .roleIds(Set.of(customer.getRoleId())).build(), actorId);

        verify(userRepository).saveAndFlush(target);
    }

    @Test
    void cannotDeactivateOwnAccount() {
        UUID userId = UUID.randomUUID();
        User currentUser = activeUser(userId, role("SHOP_OWNER"));
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> service.changeStatus(userId, ChangeUserStatusRequest.builder().targetStatus("INACTIVE").build(), userId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.SELF_STATUS_CHANGE_FORBIDDEN);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void cannotRemoveShopOwnerRoleFromLastActiveOwner() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Role owner = role("SHOP_OWNER");
        Role staff = role("SALES_STAFF");
        User target = activeUser(targetId, owner);
        when(userRepository.findWithRolesAndPermissionsById(targetId)).thenReturn(Optional.of(target));
        when(roleRepository.findAllById(Set.of(staff.getRoleId())))
                .thenReturn(List.of(staff));
        when(userRepository.lockActiveShopOwnerIds()).thenReturn(List.of(targetId));

        assertThatThrownBy(() -> service.update(targetId, UpdateUserAccountRequest.builder()
                .username("owner").email("owner@example.com").phone("0901234567")
                .roleIds(Set.of(staff.getRoleId())).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.LAST_SHOP_OWNER_PROTECTED);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void cannotRemoveShopOwnerRoleFromSelfEvenWhenMultipleActiveOwnersExist() {
        UUID actorId = UUID.randomUUID();
        Role owner = role("SHOP_OWNER");
        Role staff = role("SALES_STAFF");
        User self = activeUser(actorId, owner);
        when(userRepository.findWithRolesAndPermissionsById(actorId)).thenReturn(Optional.of(self));
        when(roleRepository.findAllById(Set.of(staff.getRoleId()))).thenReturn(List.of(staff));

        assertThatThrownBy(() -> service.update(actorId, UpdateUserAccountRequest.builder()
                .username("owner").email("owner@example.com").phone("0901234567")
                .roleIds(Set.of(staff.getRoleId())).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.SELF_ROLE_DEMOTION_FORBIDDEN);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void create_rejectsDuplicateUsernameEmailOrPhone() {
        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenAnswer(invocation -> {
            CreateUserAccountRequest request = invocation.getArgument(0);
            User user = new User();
            user.setUsername(request.getUsername().trim());
            user.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            user.setPhone(request.getPhone().trim());
            return user;
        });

        UUID actorId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        when(userRepository.existsByUsernameIgnoreCase("dup.user")).thenReturn(true);
        assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                .username("dup.user").email("unique@example.com").phone("0901234567")
                .roleIds(Set.of(roleId)).autoGenerateTemporaryPassword(true).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.DUPLICATE_ACCOUNT);

        when(userRepository.existsByUsernameIgnoreCase("unique.user")).thenReturn(false);
        when(userRepository.existsByEmailIgnoreCase("dup@example.com")).thenReturn(true);
        assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                .username("unique.user").email("dup@example.com").phone("0901234567")
                .roleIds(Set.of(roleId)).autoGenerateTemporaryPassword(true).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.DUPLICATE_ACCOUNT);

        when(userRepository.existsByEmailIgnoreCase("unique@example.com")).thenReturn(false);
        when(userRepository.existsByPhone("0909999999")).thenReturn(true);
        assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                .username("unique.user").email("unique@example.com").phone("0909999999")
                .roleIds(Set.of(roleId)).autoGenerateTemporaryPassword(true).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.DUPLICATE_ACCOUNT);

        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void changeStatus_leavingActive_revokesAllRedisSessionsAfterCommit() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, role("SALES_STAFF"));
        when(userRepository.findWithRolesAndPermissionsById(targetId)).thenReturn(Optional.of(target));
        when(userAccountMapper.toResponse(any(User.class), any(UUID.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            return UserAccountResponse.builder()
                    .id(user.getId())
                    .status(user.getStatus().name())
                    .build();
        });

        UserAccountResponse response = service.changeStatus(
                targetId,
                ChangeUserStatusRequest.builder().targetStatus("LOCKED").reason("Security audit").build(),
                actorId
        );

        assertThat(response.getStatus()).isEqualTo("LOCKED");
        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
        verify(sessionStore).revokeAllSessions(targetId, "ACCOUNT_LOCKED");
    }

    private Role role(String roleName) {
        Role role = new Role();
        role.setRoleId(UUID.randomUUID());
        role.setRoleName(roleName);
        role.setActive(true);
        return role;
    }

    private User activeUser(UUID id, Role role) {
        User user = new User();
        user.setId(id);
        user.setUsername("owner");
        user.setEmail("owner@example.com");
        user.setPhone("0901234567");
        user.setPasswordHash("hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(role));
        return user;
    }

    @Test
    void userAccountCreatedEvent_canonicalAndConvenience_enforceImmutabilityAndNullHandling() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Convenience constructor
        Map<String, Object> input = new HashMap<>();
        input.put("username", "testuser");
        input.put("avatarUrl", null);
        input.put("phone", null);

        UserAccountCreatedEvent convenienceEvent = new UserAccountCreatedEvent(actorId, userId, input);
        input.put("username", "mutated");
        input.put("extra", "forbidden");
        assertThat(convenienceEvent.data().get("username")).isEqualTo("testuser");
        assertThat(convenienceEvent.data()).doesNotContainKey("extra");
        assertThat(convenienceEvent.data().containsKey("avatarUrl")).isTrue();
        assertThat(convenienceEvent.data().get("avatarUrl")).isNull();

        assertThatThrownBy(() -> convenienceEvent.data().put("hacked", "val"))
                .isInstanceOf(UnsupportedOperationException.class);

        UserAccountCreatedEvent nullDataEvent = new UserAccountCreatedEvent(actorId, userId, null);
        assertThat(nullDataEvent.data()).isNotNull().isEmpty();
        assertThatThrownBy(() -> nullDataEvent.data().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);

        // 2. Canonical constructor
        Map<String, Object> canonicalInput = new HashMap<>();
        canonicalInput.put("status", "ACTIVE");
        canonicalInput.put("phone", null);

        UserAccountCreatedEvent canonicalEvent = new UserAccountCreatedEvent(eventId, actorId, userId, canonicalInput, now);
        canonicalInput.put("status", "MUTATED");
        assertThat(canonicalEvent.data().get("status")).isEqualTo("ACTIVE");
        assertThat(canonicalEvent.data().get("phone")).isNull();
        assertThatThrownBy(() -> canonicalEvent.data().put("status", "MUTATED"))
                .isInstanceOf(UnsupportedOperationException.class);

        UserAccountCreatedEvent canonicalNullEvent = new UserAccountCreatedEvent(eventId, actorId, userId, null, now);
        assertThat(canonicalNullEvent.data()).isNotNull().isEmpty();
    }

    @Test
    void userAccountUpdatedEvent_canonicalAndConvenience_enforceImmutabilityAndNullHandling() {
        UUID actorId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID eventId = UUID.randomUUID();
        Instant now = Instant.now();

        // 1. Convenience constructor
        Map<String, Object> oldInput = new HashMap<>();
        oldInput.put("status", "PENDING");
        oldInput.put("avatarUrl", null);

        Map<String, Object> newInput = new HashMap<>();
        newInput.put("status", "ACTIVE");
        newInput.put("avatarUrl", "http://example.com/avatar.png");

        UserAccountUpdatedEvent event = new UserAccountUpdatedEvent(actorId, userId, oldInput, newInput);
        oldInput.put("status", "MUTATED_OLD");
        newInput.put("status", "MUTATED_NEW");

        assertThat(event.oldData().get("status")).isEqualTo("PENDING");
        assertThat(event.newData().get("status")).isEqualTo("ACTIVE");
        assertThat(event.oldData().get("avatarUrl")).isNull();
        assertThat(event.newData().get("avatarUrl")).isEqualTo("http://example.com/avatar.png");

        assertThatThrownBy(() -> event.oldData().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> event.newData().put("k", "v"))
                .isInstanceOf(UnsupportedOperationException.class);

        UserAccountUpdatedEvent nullEvent = new UserAccountUpdatedEvent(actorId, userId, null, null);
        assertThat(nullEvent.oldData()).isNotNull().isEmpty();
        assertThat(nullEvent.newData()).isNotNull().isEmpty();

        // 2. Canonical constructor
        UserAccountUpdatedEvent canonicalEvent = new UserAccountUpdatedEvent(eventId, actorId, userId, oldInput, newInput, now);
        assertThat(canonicalEvent.oldData().get("status")).isEqualTo("MUTATED_OLD");
        assertThatThrownBy(() -> canonicalEvent.oldData().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> canonicalEvent.newData().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);

        UserAccountUpdatedEvent canonicalNullEvent = new UserAccountUpdatedEvent(eventId, actorId, userId, null, null, now);
        assertThat(canonicalNullEvent.oldData()).isNotNull().isEmpty();
        assertThat(canonicalNullEvent.newData()).isNotNull().isEmpty();
    }

    @Test
    void create_rejectsInvalidTemporaryPasswordPolicy() {
        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenAnswer(invocation -> {
            CreateUserAccountRequest req = invocation.getArgument(0);
            User user = new User();
            user.setUsername(req.getUsername().trim());
            user.setEmail(req.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            user.setPhone(req.getPhone().trim());
            return user;
        });
        UUID actorId = UUID.randomUUID();
        Role staff = role("SALES_STAFF");
        when(roleRepository.findAllById(Set.of(staff.getRoleId()))).thenReturn(List.of(staff));

        String[] badPasswords = {
                "Mismatch@1",
                "Has Space@1",
                "weakpass",
                "A".repeat(70) + "a1!"
        };
        for (String bad : badPasswords) {
            String confirm = "Mismatch@1".equals(bad) ? "Other@123" : bad;
            assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                    .username("staff.bad").email("bad@example.com").phone("0901234567")
                    .roleIds(Set.of(staff.getRoleId()))
                    .autoGenerateTemporaryPassword(false)
                    .temporaryPassword(bad)
                    .confirmTemporaryPassword(confirm)
                    .build(), actorId))
                    .isInstanceOf(AppException.class)
                    .extracting(error -> ((AppException) error).getErrorCode())
                    .isEqualTo(UserAccountErrorCode.INVALID_TEMPORARY_PASSWORD);
        }
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void changeStatus_rejectsInvalidOrSameOrPendingActivationStatus() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User target = activeUser(targetId, role("SALES_STAFF"));
        when(userRepository.findWithRolesAndPermissionsById(targetId)).thenReturn(Optional.of(target));

        for (String badStatus : List.of("ACTIVE", "PENDING_ACTIVATION", "UNKNOWN_STATUS")) {
            assertThatThrownBy(() -> service.changeStatus(targetId,
                    ChangeUserStatusRequest.builder().targetStatus(badStatus).build(), actorId))
                    .isInstanceOf(AppException.class)
                    .extracting(error -> ((AppException) error).getErrorCode())
                    .isEqualTo(UserAccountErrorCode.INVALID_STATUS_TRANSITION);
        }
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void create_rejectsInactiveOrMissingRolesAndDetailRejectsMissingUser() {
        UUID actorId = UUID.randomUUID();
        UUID missingUserId = UUID.randomUUID();
        when(userRepository.findWithRolesAndPermissionsById(missingUserId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.detail(missingUserId, actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.USER_NOT_FOUND);

        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenAnswer(invocation -> {
            CreateUserAccountRequest req = invocation.getArgument(0);
            User user = new User();
            user.setUsername(req.getUsername().trim());
            user.setEmail(req.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            user.setPhone(req.getPhone().trim());
            return user;
        });
        Role inactiveRole = role("INACTIVE_ROLE");
        inactiveRole.setActive(false);
        when(roleRepository.findAllById(Set.of(inactiveRole.getRoleId()))).thenReturn(List.of(inactiveRole));

        assertThatThrownBy(() -> service.create(CreateUserAccountRequest.builder()
                .username("staff.role").email("role@example.com").phone("0901234567")
                .roleIds(Set.of(inactiveRole.getRoleId()))
                .autoGenerateTemporaryPassword(true)
                .build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.ROLE_NOT_FOUND);
    }
}
