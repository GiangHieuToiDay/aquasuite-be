package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.AuthErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.response.CurrentUserResponse;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.AuthServiceImpl;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.token.JwtTokenService;
import com.aquasuite.ims.module.iam.internal.token.RedisSessionStore;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.BaseErrorCode;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.CannotCreateTransactionException;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** WORK-014: read-only current user profile resolution in {@link AuthServiceImpl}. */
@ExtendWith(MockitoExtension.class)
class AuthServiceCurrentUserTest {

    @Mock private AuthenticationManager authenticationManager;
    @Mock private UserRepository userRepository;
    @Mock private JwtTokenService jwtTokenService;
    @Mock private RedisSessionStore redisSessionStore;

    private AuthServiceImpl authService;
    private UUID userId;

    @BeforeEach
    void setUp() {
        // Real role normalization so the profile follows exactly the login response rules.
        CurrentAuthoritiesService currentAuthoritiesService =
                new CurrentAuthoritiesService(userRepository);
        authService =
                new AuthServiceImpl(
                        authenticationManager,
                        userRepository,
                        jwtTokenService,
                        redisSessionStore,
                        currentAuthoritiesService);
        userId = UUID.randomUUID();
    }

    @Test
    @DisplayName("AC-01: user ACTIVE trả đúng id, username, email, roles")
    void shouldReturnCurrentProfile() {
        Role shopOwner = role("SHOP_OWNER", true);
        User user = user(UserStatus.ACTIVE, Set.of(shopOwner));
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(jwt(userId.toString()));

        assertThat(response.getId()).isEqualTo(userId);
        assertThat(response.getUsername()).isEqualTo("alice");
        assertThat(response.getEmail()).isEqualTo("alice@aquasuite.com");
        assertThat(response.getRoles()).containsExactly("SHOP_OWNER");
        verifyNoInteractions(redisSessionStore, jwtTokenService, authenticationManager);
    }

    @Test
    @DisplayName(
            "AC-06: roles dùng cùng normalization/collision/sort với login và không chứa permission")
    void shouldNormalizeRolesLikeLoginResponse() {
        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode("catalog:read");
        Role zeta = role("zeta_role", true);
        zeta.setPermissions(Set.of(permission));
        Role alpha = role(" ROLE_alpha ", true);
        Role inactive = role("INACTIVE_ROLE", false);
        Role invalid = role("bad-name!", true);
        Role collisionA = role("ROLE_DUP", true);
        Role collisionB = role("dup", true);
        User user =
                user(
                        UserStatus.ACTIVE,
                        new HashSet<>(
                                List.of(zeta, alpha, inactive, invalid, collisionA, collisionB)));
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(jwt(userId.toString()));

        assertThat(response.getRoles()).containsExactly("ALPHA", "ZETA_ROLE");
        assertThat(response.getRoles())
                .isEqualTo(
                        new CurrentAuthoritiesService(userRepository)
                                .resolveActiveRoleNames(user.getRoles()));
    }

    @Test
    @DisplayName("AC-07: user không có active role nhận roles rỗng, không null")
    void shouldReturnEmptyRolesArray() throws Exception {
        User user = user(UserStatus.ACTIVE, Set.of(role("OLD_ROLE", false)));
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        CurrentUserResponse response = authService.getCurrentUser(jwt(userId.toString()));

        assertThat(response.getRoles()).isNotNull().isEmpty();
        JsonNode json = new ObjectMapper().valueToTree(response);
        assertThat(json.path("roles").isArray()).isTrue();
        assertThat(json.path("roles")).isEmpty();
    }

    @Test
    @DisplayName("AC-07: user không có role nào vẫn nhận roles rỗng")
    void shouldReturnEmptyRolesWhenUserHasNoRoles() {
        User user = user(UserStatus.ACTIVE, new HashSet<>());
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        assertThat(authService.getCurrentUser(jwt(userId.toString())).getRoles())
                .isNotNull()
                .isEmpty();
    }

    @Test
    @DisplayName("AC-09: payload chỉ có id, username, email, roles")
    void shouldExposeOnlyContractFields() {
        User user = user(UserStatus.ACTIVE, Set.of(role("SHOP_OWNER", true)));
        user.setPasswordHash("$2a$12$secret-hash-value");
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        JsonNode json =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .valueToTree(authService.getCurrentUser(jwt(userId.toString())));

        List<String> fields = new java.util.ArrayList<>();
        json.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id", "username", "email", "roles");
        assertThat(json.toString()).doesNotContain("secret-hash-value");
    }

    @Test
    @DisplayName("AC-05: user không tồn tại trả AUTH_001, không phải 404")
    void shouldRejectMissingUserWithAuth001() {
        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.empty());

        assertError(() -> authService.getCurrentUser(jwt(userId.toString())),
                CommonErrorCode.UNAUTHORIZED);
    }

    @Test
    @DisplayName("AC-05: user không còn ACTIVE trả AUTH_008")
    void shouldRejectInactiveUserWithAuth008() {
        for (UserStatus status :
                List.of(UserStatus.INACTIVE, UserStatus.LOCKED, UserStatus.PENDING_ACTIVATION)) {
            User user = user(status, Set.of(role("SHOP_OWNER", true)));
            when(userRepository.findWithRolesAndPermissionsById(userId))
                    .thenReturn(Optional.of(user));

            assertError(() -> authService.getCurrentUser(jwt(userId.toString())),
                    AuthErrorCode.ACCOUNT_NOT_ACTIVE);
        }
    }

    @Test
    @DisplayName("AC-08: PostgreSQL outage fail closed với AUTH_009")
    void shouldFailClosedOnDatabaseOutage() {
        when(userRepository.findWithRolesAndPermissionsById(userId))
                .thenThrow(new DataAccessResourceFailureException("db down"));

        assertError(() -> authService.getCurrentUser(jwt(userId.toString())),
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("AC-08: lỗi mở transaction khi DB down fail closed với AUTH_009")
    void shouldFailClosedOnTransactionCreationFailure() {
        when(userRepository.findWithRolesAndPermissionsById(userId))
                .thenThrow(new CannotCreateTransactionException("no connection"));

        assertError(() -> authService.getCurrentUser(jwt(userId.toString())),
                AuthErrorCode.AUTH_SERVICE_UNAVAILABLE);
    }

    @Test
    @DisplayName("Security: thiếu JWT hoặc sub không phải UUID trả AUTH_001")
    void shouldRejectMissingOrInvalidSubject() {
        assertError(() -> authService.getCurrentUser(null), CommonErrorCode.UNAUTHORIZED);
        assertError(() -> authService.getCurrentUser(jwt("not-a-uuid")),
                CommonErrorCode.UNAUTHORIZED);
        verifyNoInteractions(userRepository);
    }

    private void assertError(Runnable call, BaseErrorCode expected) {
        assertThatThrownBy(call::run)
                .isInstanceOf(AppException.class)
                .satisfies(
                        e -> assertThat(((AppException) e).getErrorCode()).isEqualTo(expected));
    }

    private Jwt jwt(String subject) {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token-value")
                .header("alg", "HS512")
                .subject(subject)
                .claim("sid", UUID.randomUUID().toString())
                .issuedAt(now)
                .expiresAt(now.plusSeconds(600))
                .build();
    }

    private User user(UserStatus status, Set<Role> roles) {
        User user = new User();
        user.setId(userId);
        user.setUsername("alice");
        user.setEmail("alice@aquasuite.com");
        user.setStatus(status);
        user.setRoles(roles);
        return user;
    }

    private Role role(String name, boolean active) {
        return new Role(UUID.randomUUID(), name, null, active, null, new HashSet<>());
    }
}
