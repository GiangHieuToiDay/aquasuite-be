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

import java.util.List;
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
        when(userAccountMapper.toNewUser(any(CreateUserAccountRequest.class))).thenAnswer(invocation -> {
            CreateUserAccountRequest request = invocation.getArgument(0);
            User user = new User();
            user.setUsername(request.getUsername().trim());
            user.setEmail(request.getEmail().trim().toLowerCase(java.util.Locale.ROOT));
            user.setPhone(request.getPhone().trim());
            user.setAvatarUrl(request.getAvatarUrl());
            return user;
        });
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void tearDown() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void create_assignsEverySelectedRole_withoutExposingTemporaryPassword() {
        UUID actorId = UUID.randomUUID();
        Role owner = role("STORE_OWNER");
        Role staff = role("SALES_STAFF");
        Set<UUID> roleIds = Set.of(owner.getRoleId(), staff.getRoleId());
        when(roleRepository.findAllById(roleIds)).thenReturn(List.of(owner, staff));
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
                .username("staff.one").email("staff.one@example.com").phone("0901234567").roleIds(roleIds)
                .autoGenerateTemporaryPassword(false).temporaryPassword("ValidPassword@1")
                .confirmTemporaryPassword("ValidPassword@1").build(), actorId);

        assertThat(response.getStatus()).isEqualTo("ACTIVE");
        assertThat(response.getProvider()).isEqualTo("LOCAL");
        assertThat(response.isVerified()).isFalse();
        assertThat(response.getRoles()).extracting(role -> role.getCode()).containsExactlyInAnyOrder("STORE_OWNER", "SALES_STAFF");
        verify(userRepository).markRolesAssignedBy(response.getId(), actorId);
        ArgumentCaptor<UserAccountCreatedEvent> event = ArgumentCaptor.forClass(UserAccountCreatedEvent.class);
        verify(eventPublisher).publishEvent(event.capture());
        assertThat(event.getValue().data()).doesNotContainKeys("password", "passwordHash", "temporaryPassword");
    }

    @Test
    void cannotDeactivateOwnAccount() {
        UUID userId = UUID.randomUUID();
        User currentUser = activeUser(userId, role("STORE_OWNER"));
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(currentUser));

        assertThatThrownBy(() -> service.changeStatus(userId, ChangeUserStatusRequest.builder().targetStatus("INACTIVE").build(), userId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.SELF_STATUS_CHANGE_FORBIDDEN);
        verify(userRepository, never()).saveAndFlush(any(User.class));
    }

    @Test
    void cannotRemoveStoreOwnerRoleFromLastActiveOwner() {
        UUID actorId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        Role owner = role("STORE_OWNER");
        Role staff = role("SALES_STAFF");
        User target = activeUser(targetId, owner);
        when(userRepository.findWithRolesAndPermissionsById(targetId)).thenReturn(Optional.of(target));
        when(roleRepository.findAllById(Set.of(staff.getRoleId()))).thenReturn(List.of(staff));
        when(userRepository.lockActiveStoreOwnerIds()).thenReturn(List.of(targetId));

        assertThatThrownBy(() -> service.update(targetId, UpdateUserAccountRequest.builder()
                .username("owner").email("owner@example.com").phone("0901234567")
                .roleIds(Set.of(staff.getRoleId())).build(), actorId))
                .isInstanceOf(AppException.class)
                .extracting(error -> ((AppException) error).getErrorCode())
                .isEqualTo(UserAccountErrorCode.LAST_STORE_OWNER_PROTECTED);
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
}
