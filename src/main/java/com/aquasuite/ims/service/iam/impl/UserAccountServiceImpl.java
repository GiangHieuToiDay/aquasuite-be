package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.event.iam.UserAccountCreatedEvent;
import com.aquasuite.ims.event.iam.UserAccountStatusChangedEvent;
import com.aquasuite.ims.event.iam.UserAccountUpdatedEvent;

import com.aquasuite.ims.enums.iam.AuthProvider;
import com.aquasuite.ims.entity.iam.Role;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.exception.iam.UserAccountErrorCode;
import com.aquasuite.ims.mapper.iam.UserAccountMapper;
import com.aquasuite.ims.integration.iam.AccountCredentialsNotificationPort;
import com.aquasuite.ims.dto.iam.request.ChangeUserStatusRequest;
import com.aquasuite.ims.dto.iam.request.CreateUserAccountRequest;
import com.aquasuite.ims.dto.iam.request.UpdateUserAccountRequest;
import com.aquasuite.ims.dto.iam.response.AccountPageResponse;
import com.aquasuite.ims.dto.iam.response.AccountStatsResponse;
import com.aquasuite.ims.dto.iam.response.UserAccountResponse;
import com.aquasuite.ims.repository.iam.RoleRepository;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.service.iam.UserAccountService;
import com.aquasuite.ims.repository.iam.store.RedisSessionStore;
import com.aquasuite.ims.exception.AppException;
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

import com.aquasuite.ims.dto.iam.request.CreateCreditApprovalGrantRequest;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantDto;
import com.aquasuite.ims.dto.iam.response.CreditApprovalGrantResponse;
import com.aquasuite.ims.enums.iam.AuthProvider;
import com.aquasuite.ims.enums.iam.UserStatus;
import com.aquasuite.ims.exception.iam.AuthErrorCode;
import com.aquasuite.ims.exception.iam.CreditApprovalErrorCode;
import com.aquasuite.ims.repository.iam.store.CreditApprovalGrantStore;
import org.springframework.beans.factory.annotation.Autowired;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Service
@Slf4j
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountServiceImpl implements UserAccountService {
    private static final Duration GRANT_TTL = Duration.ofMinutes(2);
    private static final Pattern PASSWORD_POLICY = Pattern.compile("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$");
    private static final char[] TEMP_PASSWORD_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();

    UserRepository userRepository;
    RoleRepository roleRepository;
    PasswordEncoder passwordEncoder;
    ApplicationEventPublisher eventPublisher;
    AccountCredentialsNotificationPort notificationPort;
    RedisSessionStore sessionStore;
    UserAccountMapper userAccountMapper;
    CreditApprovalGrantStore grantStore;
    SecureRandom secureRandom = new SecureRandom();

    @Autowired
    public UserAccountServiceImpl(
            UserRepository userRepository,
            @Autowired(required = false) RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            @Autowired(required = false) ApplicationEventPublisher eventPublisher,
            @Autowired(required = false) AccountCredentialsNotificationPort notificationPort,
            @Autowired(required = false) RedisSessionStore sessionStore,
            @Autowired(required = false) UserAccountMapper userAccountMapper,
            @Autowired(required = false) CreditApprovalGrantStore grantStore) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.eventPublisher = eventPublisher;
        this.notificationPort = notificationPort;
        this.sessionStore = sessionStore;
        this.userAccountMapper = userAccountMapper;
        this.grantStore = grantStore;
    }

    public UserAccountServiceImpl(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            ApplicationEventPublisher eventPublisher,
            AccountCredentialsNotificationPort notificationPort,
            RedisSessionStore sessionStore,
            UserAccountMapper userAccountMapper) {
        this(userRepository, roleRepository, passwordEncoder, eventPublisher, notificationPort, sessionStore, userAccountMapper, null);
    }

    public UserAccountServiceImpl(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            CreditApprovalGrantStore grantStore) {
        this(userRepository, null, passwordEncoder, null, null, null, null, grantStore);
    }

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
        assertRoleChangeAllowed(user, roles, actorId);
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
        if (roleIds == null || roleIds.isEmpty()) {
            throw new AppException(UserAccountErrorCode.ROLE_NOT_FOUND);
        }
        Set<Role> roles = new HashSet<>(roleRepository.findAllById(roleIds));
        if (roles.size() != roleIds.size() || roles.stream().anyMatch(role -> !role.isActive())) {
            throw new AppException(UserAccountErrorCode.ROLE_NOT_FOUND);
        }
        return roles;
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

    private void assertRoleChangeAllowed(User user, Collection<Role> replacementRoles, UUID actorId) {
        if (user.getId() != null && user.getId().equals(actorId) && isShopOwner(user) && !isShopOwner(replacementRoles)) {
            throw new AppException(UserAccountErrorCode.SELF_ROLE_DEMOTION_FORBIDDEN);
        }
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
        if (password == null
                || !password.equals(confirmation)
                || password.chars().anyMatch(Character::isWhitespace)
                || password.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72
                || !PASSWORD_POLICY.matcher(password).matches())
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

    // ── Credit Approval Grants (from CreditApprovalGrantServiceImpl) ─────────

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
