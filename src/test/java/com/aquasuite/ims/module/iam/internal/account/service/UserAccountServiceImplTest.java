package com.aquasuite.ims.module.iam.internal.account.service;

import com.aquasuite.ims.module.iam.internal.error.UserAccountErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.ChangeUserStatusRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.UserAccountResponse;
import com.aquasuite.ims.module.iam.internal.mapper.UserAccountMapper;
import com.aquasuite.ims.module.iam.internal.notification.AccountCredentialsNotificationPort;
import com.aquasuite.ims.module.iam.internal.service.impl.UserAccountServiceImpl;
import com.aquasuite.ims.module.iam.api.event.UserAccountCreatedEvent;
import com.aquasuite.ims.module.iam.api.event.UserAccountUpdatedEvent;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
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
    void create_assignsOnlySelectedRole_withoutExposingTemporaryPassword() {
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
        when(roleRepository.findById(staff.getRoleId())).thenReturn(Optional.of(staff));
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
                            .map(role -> com.aquasuite.ims.module.iam.internal.payload.response.RoleOptionResponse.builder()
                                    .id(role.getRoleId()).code(role.getRoleName()).description(role.getDescription()).build())
                            .toList())
                    .build();
        });

        UserAccountResponse response = service.create(CreateUserAccountRequest.builder()
                .username("staff.one").email("staff.one@example.com").phone("0901234567").roleId(staff.getRoleId())
                .autoGenerateTemporaryPassword(false).temporaryPassword("ValidPassword@1")
                .confirmTemporaryPassword("ValidPassword@1").build(), actorId);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getProvider()).isEqualTo("LOCAL");
        assertThat(response.isVerified()).isFalse();
        assertThat(response.getRoles()).extracting(role -> role.getCode()).containsExactly("SALES_STAFF");
        verify(userRepository).markRolesAssignedBy(response.getId(), actorId);
        ArgumentCaptor<UserAccountCreatedEvent> event = ArgumentCaptor.forClass(UserAccountCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().data()).doesNotContainKeys("password", "passwordHash", "temporaryPassword");
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
        when(roleRepository.findById(staff.getRoleId())).thenReturn(Optional.of(staff));
        when(userRepository.lockActiveShopOwnerIds()).thenReturn(List.of(targetId));

        assertThatThrownBy(() -> service.update(targetId, UpdateUserAccountRequest.builder()
                .username("owner").email("owner@example.com").phone("0901234567")
                .roleId(staff.getRoleId()).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.LAST_SHOP_OWNER_PROTECTED);
        verify(userRepository, never()).saveAndFlush(any(User.class));
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
}
