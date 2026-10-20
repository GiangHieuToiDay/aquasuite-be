package com.aquasuite.ims.module.iam.internal.bootstrap;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.authorization.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallbackWithoutResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevelopmentDataInitializerTest {

    private RoleRepository roleRepository;
    private UserRepository userRepository;
    private PasswordEncoder passwordEncoder;
    private TransactionTemplate transactionTemplate;
    private Environment environment;

    private boolean enabled;
    private String configuredPassword;
    private DevelopmentDataInitializer initializer;

    @BeforeEach
    void setUp() {
        roleRepository = mock(RoleRepository.class);
        userRepository = mock(UserRepository.class);
        passwordEncoder = new BCryptPasswordEncoder(12);
        transactionTemplate = mock(TransactionTemplate.class);
        environment = mock(Environment.class);

        enabled = false;
        configuredPassword = "StrongDevPassword123!";

        // Wire transactionTemplate to synchronously execute the callback
        doAnswer(invocation -> {
            Consumer<TransactionStatus> callback = invocation.getArgument(0);
            callback.accept(mock(TransactionStatus.class));
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        // Default mock behaviors
        when(roleRepository.save(any(Role.class))).thenAnswer(inv -> inv.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        createInitializer();
    }

    private void createInitializer() {
        when(environment.getProperty("aquasuite.iam.dev-seed.enabled")).thenReturn(String.valueOf(enabled));
        initializer = new DevelopmentDataInitializer(
                roleRepository,
                userRepository,
                passwordEncoder,
                transactionTemplate,
                environment
        );
        initializer.setPasswordSupplier(() -> configuredPassword);
    }

    private void setDevEnvironment() {
        enabled = true;
        when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
        when(environment.getProperty("aquasuite.config.environment")).thenReturn("dev");
        createInitializer();
    }

    @Nested
    @DisplayName("1. Guard and Production Protection")
    class GuardTests {

        @Test
        @DisplayName("Flag false: no seed and zero repository calls")
        void shouldSkipWhenDisabled() {
            enabled = false;
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
            when(environment.getProperty("aquasuite.config.environment")).thenReturn("dev");
            createInitializer();

            boolean result = initializer.initialize();

            assertThat(result).isFalse();
            verify(transactionTemplate, never()).executeWithoutResult(any());
            verify(roleRepository, never()).findByRoleNameIgnoreCase(anyString());
            verify(userRepository, never()).findByEmailIgnoreCase(anyString());
        }

        @Test
        @DisplayName("Dev profile + environment dev + flag true + password set: seed succeeds")
        void shouldProceedWhenAllDevConditionsMet() {
            setDevEnvironment();

            boolean result = initializer.initialize();

            assertThat(result).isTrue();
            verify(transactionTemplate, times(1)).executeWithoutResult(any());
            verify(roleRepository, times(6)).save(any(Role.class));
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Test profile only: zero writes and fail-closed")
        void shouldBlockOnTestProfile() {
            enabled = true;
            when(environment.getActiveProfiles()).thenReturn(new String[]{"test"});
            when(environment.getProperty("aquasuite.config.environment")).thenReturn("dev");
            createInitializer();

            boolean result = initializer.initialize();

            assertThat(result).isFalse();
            verify(transactionTemplate, never()).executeWithoutResult(any());
        }

        @Test
        @DisplayName("Prod profile: zero writes even if flag is true")
        void shouldBlockOnProdProfile() {
            enabled = true;
            when(environment.getActiveProfiles()).thenReturn(new String[]{"prod"});
            when(environment.getProperty("aquasuite.config.environment")).thenReturn("dev");
            createInitializer();

            boolean result = initializer.initialize();

            assertThat(result).isFalse();
            verify(transactionTemplate, never()).executeWithoutResult(any());
        }

        @Test
        @DisplayName("Multiple profiles containing prod + dev: zero writes")
        void shouldBlockOnProdAndDevProfiles() {
            enabled = true;
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev", "prod"});
            when(environment.getProperty("aquasuite.config.environment")).thenReturn("dev");
            createInitializer();

            boolean result = initializer.initialize();

            assertThat(result).isFalse();
            verify(transactionTemplate, never()).executeWithoutResult(any());
        }

        @Test
        @DisplayName("Default / unknown environment: zero writes")
        void shouldBlockOnDefaultEnvironment() {
            enabled = true;
            when(environment.getActiveProfiles()).thenReturn(new String[]{"dev"});
            when(environment.getProperty("aquasuite.config.environment")).thenReturn("default");
            createInitializer();

            boolean result = initializer.initialize();

            assertThat(result).isFalse();
            verify(transactionTemplate, never()).executeWithoutResult(any());
        }
    }

    @Nested
    @DisplayName("2. Environment Password Validation")
    class PasswordValidationTests {

        @Test
        @DisplayName("Missing password (null) throws IllegalStateException before writes")
        void shouldThrowWhenPasswordIsNull() {
            setDevEnvironment();
            configuredPassword = null;
            createInitializer();

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AQUASUITE_DEV_OWNER_PASSWORD is null or blank");

            verify(transactionTemplate, never()).executeWithoutResult(any());
            verify(roleRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Blank password throws IllegalStateException before writes")
        void shouldThrowWhenPasswordIsBlank() {
            setDevEnvironment();
            configuredPassword = "   ";
            createInitializer();

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("AQUASUITE_DEV_OWNER_PASSWORD is null or blank");

            verify(transactionTemplate, never()).executeWithoutResult(any());
            verify(roleRepository, never()).save(any());
            verify(userRepository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("3. Canonical Roles Seed and Constraints")
    class RolesSeedTests {

        @Test
        @DisplayName("Seeds exactly 6 canonical roles, never creates GUEST or permissions")
        void shouldSeedExactSixRoles() {
            setDevEnvironment();

            initializer.initialize();

            ArgumentCaptor<Role> roleCaptor = ArgumentCaptor.forClass(Role.class);
            verify(roleRepository, times(6)).save(roleCaptor.capture());

            List<Role> savedRoles = roleCaptor.getAllValues();
            Set<String> savedRoleNames = savedRoles.stream().map(Role::getRoleName).collect(Collectors.toSet());

            assertThat(savedRoleNames).containsExactlyInAnyOrder(
                    "SHOP_OWNER",
                    "SALES_STAFF",
                    "ACCOUNTANT",
                    "TECHNICAL_STAFF",
                    "SHIPPER",
                    "CUSTOMER"
            );

            assertThat(savedRoleNames).doesNotContain("GUEST");
            assertThat(savedRoleNames).doesNotContain("ROLE_SHOP_OWNER");

            for (Role role : savedRoles) {
                assertThat(role.isActive()).isTrue();
                assertThat(role.getRoleId()).isNotNull();
                assertThat(role.getPermissions()).isEmpty();
            }
        }

        @Test
        @DisplayName("Existing canonical roles are reused and not overwritten")
        void shouldReuseExistingRoles() {
            setDevEnvironment();

            for (DevelopmentDataInitializer.RoleDefinition def : DevelopmentDataInitializer.CANONICAL_ROLES) {
                Role existing = new Role(UUID.randomUUID(), def.name(), def.description(), true, null, Set.of());
                when(roleRepository.findByRoleNameIgnoreCase(def.name())).thenReturn(Optional.of(existing));
            }

            initializer.initialize();

            // Zero new roles saved
            verify(roleRepository, never()).save(any());
            // Owner still created
            verify(userRepository, times(1)).save(any(User.class));
        }

        @Test
        @DisplayName("Existing canonical role with case mismatch throws SeedConflictException")
        void shouldThrowOnRoleCaseMismatch() {
            setDevEnvironment();

            Role mismatchRole = new Role(UUID.randomUUID(), "shop_owner", "Chủ cửa hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(mismatchRole));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Role name case mismatch for SHOP_OWNER");
        }

        @Test
        @DisplayName("Existing canonical role that is inactive throws SeedConflictException")
        void shouldThrowOnInactiveRole() {
            setDevEnvironment();

            Role inactiveRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", false, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(inactiveRole));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Role SHOP_OWNER exists in database but is inactive");
        }
    }

    @Nested
    @DisplayName("4. Owner User Seed, BCrypt and Idempotency")
    class OwnerSeedTests {

        @Test
        @DisplayName("Creates owner with exact email, username, ACTIVE status, BCrypt hash and SHOP_OWNER role")
        void shouldCreateOwnerCorrectly() {
            setDevEnvironment();

            initializer.initialize();

            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository, times(1)).save(userCaptor.capture());

            User user = userCaptor.getValue();
            assertThat(user.getEmail()).isEqualTo("owner@aquasuite.local");
            assertThat(user.getUsername()).isEqualTo("owner_test");
            assertThat(user.getStatus()).isEqualTo(UserStatus.ACTIVE);
            assertThat(user.getId()).isNotNull();

            assertThat(passwordEncoder.matches("StrongDevPassword123!", user.getPasswordHash())).isTrue();

            assertThat(user.getRoles()).hasSize(1);
            Role assignedRole = user.getRoles().iterator().next();
            assertThat(assignedRole.getRoleName()).isEqualTo("SHOP_OWNER");
        }

        @Test
        @DisplayName("Existing matching owner retains record and BCrypt hash without save/overwrite")
        void shouldRetainMatchingOwner() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            for (DevelopmentDataInitializer.RoleDefinition def : DevelopmentDataInitializer.CANONICAL_ROLES) {
                Role r = def.name().equals("SHOP_OWNER") ? ownerRole : new Role(UUID.randomUUID(), def.name(), def.description(), true, null, Set.of());
                when(roleRepository.findByRoleNameIgnoreCase(def.name())).thenReturn(Optional.of(r));
            }

            String existingHash = passwordEncoder.encode("StrongDevPassword123!");
            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("owner@aquasuite.local");
            existingOwner.setUsername("owner_test");
            existingOwner.setStatus(UserStatus.ACTIVE);
            existingOwner.setPasswordHash(existingHash);
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            boolean result = initializer.initialize();

            assertThat(result).isTrue();
            verify(userRepository, never()).save(any());
            assertThat(existingOwner.getPasswordHash()).isEqualTo(existingHash);
        }

        @Test
        @DisplayName("CR-V6-02: Existing owner with email case mismatch throws SeedConflictException")
        void shouldThrowOnOwnerEmailCaseMismatch() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(ownerRole));

            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("OWNER@AQUASUITE.LOCAL");
            existingOwner.setUsername("owner_test");
            existingOwner.setStatus(UserStatus.ACTIVE);
            existingOwner.setPasswordHash(passwordEncoder.encode("StrongDevPassword123!"));
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Owner email case mismatch");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("CR-V6-02: Existing owner with username case mismatch throws SeedConflictException")
        void shouldThrowOnOwnerUsernameCaseMismatch() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(ownerRole));

            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("owner@aquasuite.local");
            existingOwner.setUsername("Owner_Test");
            existingOwner.setStatus(UserStatus.ACTIVE);
            existingOwner.setPasswordHash(passwordEncoder.encode("StrongDevPassword123!"));
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Owner username case mismatch");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Existing owner with wrong password throws SeedConflictException")
        void shouldThrowOnPasswordMismatch() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(ownerRole));

            String differentHash = passwordEncoder.encode("DifferentPassword!");
            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("owner@aquasuite.local");
            existingOwner.setUsername("owner_test");
            existingOwner.setStatus(UserStatus.ACTIVE);
            existingOwner.setPasswordHash(differentHash);
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("password hash does not match");

            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("Existing owner with non-ACTIVE status throws SeedConflictException")
        void shouldThrowOnNonActiveStatus() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(ownerRole));

            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("owner@aquasuite.local");
            existingOwner.setUsername("owner_test");
            existingOwner.setStatus(UserStatus.LOCKED);
            existingOwner.setPasswordHash(passwordEncoder.encode("StrongDevPassword123!"));
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("non-ACTIVE status");
        }

        @Test
        @DisplayName("Existing owner with wrong or extra roles throws SeedConflictException")
        void shouldThrowOnRoleSetMismatch() {
            setDevEnvironment();

            Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
            Role salesRole = new Role(UUID.randomUUID(), "SALES_STAFF", "Nhân viên bán hàng", true, null, Set.of());
            when(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).thenReturn(Optional.of(ownerRole));

            User existingOwner = new User();
            existingOwner.setId(UUID.randomUUID());
            existingOwner.setEmail("owner@aquasuite.local");
            existingOwner.setUsername("owner_test");
            existingOwner.setStatus(UserStatus.ACTIVE);
            existingOwner.setPasswordHash(passwordEncoder.encode("StrongDevPassword123!"));
            existingOwner.setRoles(new HashSet<>(Set.of(ownerRole, salesRole)));

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(existingOwner));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(existingOwner));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("role set mismatch");
        }

        @Test
        @DisplayName("Owner identity collision between different users throws SeedConflictException")
        void shouldThrowOnIdentityCollision() {
            setDevEnvironment();

            User user1 = new User();
            user1.setId(UUID.randomUUID());
            user1.setEmail("owner@aquasuite.local");

            User user2 = new User();
            user2.setId(UUID.randomUUID());
            user2.setUsername("owner_test");

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(user1));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(user2));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Owner identity collision");
        }

        @Test
        @DisplayName("Partial owner identity collision (only email exists) throws SeedConflictException")
        void shouldThrowOnPartialCollisionEmail() {
            setDevEnvironment();

            User user1 = new User();
            user1.setId(UUID.randomUUID());
            user1.setEmail("owner@aquasuite.local");

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(user1));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.empty());

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Owner identity partial collision");
        }

        @Test
        @DisplayName("Partial owner identity collision (only username exists) throws SeedConflictException")
        void shouldThrowOnPartialCollisionUsername() {
            setDevEnvironment();

            User user1 = new User();
            user1.setId(UUID.randomUUID());
            user1.setUsername("owner_test");

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.empty());
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(user1));

            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("Owner identity partial collision");
        }
    }

    @Nested
    @DisplayName("5. TransactionTemplate Execution and Rollback Propagation")
    class TransactionTests {

        @Test
        @DisplayName("Late owner conflict after roles created propagates exception out of callback for transaction rollback")
        void shouldPropagateExceptionWhenOwnerConflictOccursAfterRolesCreated() {
            setDevEnvironment();

            // Setup: owner email taken by non-ACTIVE user
            User conflictUser = new User();
            conflictUser.setId(UUID.randomUUID());
            conflictUser.setEmail("owner@aquasuite.local");
            conflictUser.setUsername("owner_test");
            conflictUser.setStatus(UserStatus.LOCKED);
            conflictUser.setPasswordHash(passwordEncoder.encode("StrongDevPassword123!"));
            conflictUser.setRoles(Set.of());

            when(userRepository.findByEmailIgnoreCase("owner@aquasuite.local")).thenReturn(Optional.of(conflictUser));
            when(userRepository.findByUsernameIgnoreCase("owner_test")).thenReturn(Optional.of(conflictUser));

            // Roles are saved first during callback execution
            assertThatThrownBy(() -> initializer.initialize())
                    .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                    .hasMessageContaining("non-ACTIVE status");

            // Verify roles WERE saved in the transaction callback before the owner check failed
            verify(roleRepository, times(6)).save(any(Role.class));
            // Verify owner was NOT saved
            verify(userRepository, never()).save(any());
            // Because the exception escaped the TransactionTemplate callback, the transaction rolls back
        }
    }

    @Nested
    @DisplayName("6. Spring Context and Bean Wiring")
    class SpringContextWiringTests {

        @Test
        @DisplayName("Spring ApplicationContext creates DevelopmentDataInitializer using single constructor without ambiguity")
        void shouldBeCreatedBySpringContextWithoutConstructorAmbiguity() {
            new ApplicationContextRunner()
                    .withBean(RoleRepository.class, () -> roleRepository)
                    .withBean(UserRepository.class, () -> userRepository)
                    .withBean(PasswordEncoder.class, () -> passwordEncoder)
                    .withBean(TransactionTemplate.class, () -> transactionTemplate)
                    .withBean(DevelopmentDataInitializer.class)
                    .run(context -> {
                        assertThat(context).hasNotFailed();
                        assertThat(context).hasSingleBean(DevelopmentDataInitializer.class);
                    });
        }
    }
}
