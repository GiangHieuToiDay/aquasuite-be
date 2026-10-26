package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.api.event.UserAccountCreatedEvent;
import com.aquasuite.ims.module.iam.api.event.UserAccountStatusChangedEvent;
import com.aquasuite.ims.module.iam.api.event.UserAccountUpdatedEvent;

import com.aquasuite.ims.module.iam.internal.entity.AuthProvider;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.UserAccountErrorCode;
import com.aquasuite.ims.module.iam.internal.mapper.UserAccountMapper;
import com.aquasuite.ims.module.iam.internal.notification.AccountCredentialsNotificationPort;
import com.aquasuite.ims.module.iam.internal.payload.request.ChangeUserStatusRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountStatsResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.UserAccountResponse;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.UserAccountService;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.AppException;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@RequiredArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountServiceImpl implements UserAccountService {
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    private static final char[] TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    ApplicationEventPublisher eventPublisher;
    AccountCredentialsNotificationPort notificationPort;
    RedisSessionStore sessionStore;
    UserAccountMapper userAccountMapper;

    @Override
    @Transactional(readOnly = true)
    public AccountStatsResponse stats() {
        return AccountStatsResponse.builder()
                .total(userRepository.count())
                .active(userRepository.countByStatus(UserStatus.ACTIVE))
                .inactive(userRepository.countByStatus(UserStatus.INACTIVE))
                .locked(userRepository.countByStatus(UserStatus.LOCKED))
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public AccountPageResponse search(
            String keyword,
            UUID roleId,
            String status,
            Pageable pageable,
            UUID currentUserId
    ) {

        String normalizedKeyword = keyword == null
                ? ""
                : keyword.trim().toLowerCase();

        Page<User> result = userRepository.search(
                normalizedKeyword,
                roleId,
                parseFilterStatus(status),
                pageable
        );

        return AccountPageResponse.builder()
                .content(
                        result.getContent()
                                .stream()
                                .map(user -> toResponse(user, currentUserId))
                                .toList()
                )
                .page(result.getNumber())
                .size(result.getSize())
                .totalElements(result.getTotalElements())
                .totalPages(result.getTotalPages())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public UserAccountResponse detail(UUID id, UUID currentUserId) {
        return toResponse(findUser(id), currentUserId);
    }

    @Override
    @Transactional
    public UserAccountResponse create(CreateUserAccountRequest request, UUID actorId) {
        User user = userAccountMapper.toNewUser(request);
        assertCreateUnique(user.getUsername(), user.getEmail(), user.getPhone());
        Set<Role> roles = resolveRoles(request.getRoleIds());
        String rawPassword = request.isAutoGenerateTemporaryPassword() ? generateTemporaryPassword()
                : validateProvidedTemporaryPassword(request.getTemporaryPassword(), request.getConfirmTemporaryPassword());

        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setStatus(UserStatus.ACTIVE);
        user.setProvider(AuthProvider.LOCAL);
        user.setVerified(false);
        user.setRoles(roles);
        persist(user);
        userRepository.markRolesAssignedBy(user.getId(), actorId);

        eventPublisher.publishEvent(new UserAccountCreatedEvent(actorId, user.getId(), accountSnapshot(user)));
        String emailForDelivery = user.getEmail();
        String usernameForDelivery = user.getUsername();
        afterCommit(() -> notificationPort.sendInitialCredentials(user.getId(), emailForDelivery, usernameForDelivery, rawPassword));
        return toResponse(user, actorId);
    }

    @Override
    @Transactional
    public UserAccountResponse update(UUID id, UpdateUserAccountRequest request, UUID actorId) {
        User user = findUser(id);
        Map<String, Object> oldData = accountSnapshot(user);
        Set<Role> roles = resolveRoles(request.getRoleIds());
        assertRoleChangeAllowed(user, roles);
        userAccountMapper.updateUser(request, user);
        assertUpdateUnique(user.getId(), user.getUsername(), user.getEmail(), user.getPhone());
        user.setRoles(roles);
        persist(user);
        userRepository.markRolesAssignedBy(user.getId(), actorId);
        eventPublisher.publishEvent(new UserAccountUpdatedEvent(actorId, user.getId(), oldData, accountSnapshot(user)));
        return toResponse(user, actorId);
    }

    @Override
    @Transactional
    public UserAccountResponse changeStatus(UUID id, ChangeUserStatusRequest request, UUID actorId) {
        User user = findUser(id);
        UserStatus targetStatus = parseTransitionStatus(request.getTargetStatus());
        if (user.getId().equals(actorId) && targetStatus != UserStatus.ACTIVE) {
            throw new AppException(UserAccountErrorCode.SELF_STATUS_CHANGE_FORBIDDEN);
        }
        if (user.getStatus() == targetStatus) {
            throw new AppException(UserAccountErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (user.getStatus() == UserStatus.ACTIVE && targetStatus != UserStatus.ACTIVE && isShopOwner(user)
                && activeShopOwnerIdsLocked().size() <= 1) {
            throw new AppException(UserAccountErrorCode.LAST_SHOP_OWNER_PROTECTED);
        }
        UserStatus old = user.getStatus();
        user.setStatus(targetStatus);
        persist(user);
        eventPublisher.publishEvent(new UserAccountStatusChangedEvent(actorId, user.getId(), old.name(), targetStatus.name(), blankToNull(request.getReason())));
        if (targetStatus != UserStatus.ACTIVE) {
            afterCommit(() -> sessionStore.revokeAllSessions(user.getId(), "ACCOUNT_" + targetStatus.name()));
        }
        return toResponse(user, actorId);
    }

    private User findUser(UUID id) {
        return userRepository.findWithRolesAndPermissionsById(id)
                .orElseThrow(() -> new AppException(UserAccountErrorCode.USER_NOT_FOUND));
    }

    private Set<Role> resolveRoles(Set<UUID> roleIds) {
        List<Role> roles = roleRepository.findAllById(roleIds);
        if (roles.size() != roleIds.size() || roles.stream().anyMatch(role -> !role.isActive())) {
            throw new AppException(UserAccountErrorCode.ROLE_NOT_FOUND);
        }
        return new HashSet<>(roles);
    }

    private void assertCreateUnique(String username, String email, String phone) {
        if (userRepository.existsByUsernameIgnoreCase(username) || userRepository.existsByEmailIgnoreCase(email)
                || userRepository.existsByPhone(phone)) throw new AppException(UserAccountErrorCode.DUPLICATE_ACCOUNT);
    }

    private void assertUpdateUnique(UUID id, String username, String email, String phone) {
        if (userRepository.existsByUsernameIgnoreCaseAndIdNot(username, id)
                || userRepository.existsByEmailIgnoreCaseAndIdNot(email, id)
                || userRepository.existsByPhoneAndIdNot(phone, id)) throw new AppException(UserAccountErrorCode.DUPLICATE_ACCOUNT);
    }

    private void assertRoleChangeAllowed(User user, Set<Role> replacementRoles) {
        if (user.getStatus() == UserStatus.ACTIVE && isShopOwner(user) && !isShopOwner(replacementRoles)
                && activeShopOwnerIdsLocked().size() <= 1) {
            throw new AppException(UserAccountErrorCode.LAST_SHOP_OWNER_PROTECTED);
        }
    }

    private boolean isShopOwner(User user) { return isShopOwner(user.getRoles()); }
    private List<UUID> activeShopOwnerIdsLocked() { return userRepository.lockActiveShopOwnerIds(); }
    private boolean isShopOwner(Collection<Role> roles) {
        return roles.stream().map(Role::getRoleName).filter(Objects::nonNull)
                .map(name -> name.trim().toUpperCase(Locale.ROOT))
                .anyMatch(name -> name.equals("SHOP_OWNER") || name.equals("ROLE_SHOP_OWNER"));
    }

    private void persist(User user) {
        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException exception) {
            if (isAccountIdentityConstraint(exception)) {
                throw new AppException(UserAccountErrorCode.DUPLICATE_ACCOUNT);
            }
            throw exception;
        }
    }

    private boolean isAccountIdentityConstraint(Throwable exception) {
        Set<String> identityConstraints = Set.of("uq_users_phone", "uq_users_username_lower", "uq_users_email_lower");
        Throwable current = exception;
        while (current != null) {
            if (current instanceof org.hibernate.exception.ConstraintViolationException violation
                    && identityConstraints.contains(violation.getConstraintName())) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private UserAccountResponse toResponse(User user, UUID currentUserId) {
        return userAccountMapper.toResponse(user, currentUserId);
    }

    private Map<String, Object> accountSnapshot(User user) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("username", user.getUsername()); snapshot.put("email", user.getEmail());
        snapshot.put("phone", user.getPhone()); snapshot.put("avatarUrl", user.getAvatarUrl());
        snapshot.put("status", user.getStatus().name()); snapshot.put("provider", user.getProvider().name());
        snapshot.put("isVerified", user.isVerified());
        snapshot.put("roles", user.getRoles().stream().map(Role::getRoleName).toList());
        return snapshot;
    }

    private String validateProvidedTemporaryPassword(String password, String confirmation) {
        if (password == null || !password.equals(confirmation) || !PASSWORD_POLICY.matcher(password).matches())
            throw new AppException(UserAccountErrorCode.INVALID_TEMPORARY_PASSWORD);
        return password;
    }

    private String generateTemporaryPassword() {
        SecureRandom random = new SecureRandom(); StringBuilder value = new StringBuilder(14);
        value.append('A').append('a').append('1').append('!');
        while (value.length() < 14) value.append(TEMP_PASSWORD_ALPHABET[random.nextInt(TEMP_PASSWORD_ALPHABET.length)]);
        return value.toString();
    }

    private UserStatus parseFilterStatus(String status) {
        if (status == null || status.isBlank()) return null;
        try { return UserStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) { throw new AppException(UserAccountErrorCode.INVALID_STATUS_TRANSITION); }
    }
    private UserStatus parseTransitionStatus(String status) {
        UserStatus value = parseFilterStatus(status);
        if (value == null || value == UserStatus.PENDING_ACTIVATION) throw new AppException(UserAccountErrorCode.INVALID_STATUS_TRANSITION);
        return value;
    }
    private String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private void afterCommit(Runnable operation) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    operation.run();
                } catch (RuntimeException exception) {
                    log.error("Post-commit account side effect failed; the committed account change is retained for retry/reconciliation.", exception);
                }
            }
        });
    }
}
