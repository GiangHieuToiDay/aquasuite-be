package com.aquasuite.ims.module.iam.internal.bootstrap;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DevelopmentDataInitializerRollbackIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions");
            stmt.executeUpdate("DELETE FROM public.user_roles");
            stmt.executeUpdate("DELETE FROM public.users");
            stmt.executeUpdate("DELETE FROM public.roles WHERE role_name NOT IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER')");
        }
    }

    @Test
    @DisplayName("CR-V6-03: Late owner conflict after createRoles() rolls back entire transaction leaving database uncorrupted")
    void shouldRollbackAllRolesWhenOwnerConflictOccursInSingleTransaction() {
        // 1. Prepare clean test database with pre-existing conflicting user
        User conflictUser = new User();
        conflictUser.setId(UUID.randomUUID());
        conflictUser.setEmail(DevelopmentDataInitializer.OWNER_EMAIL);
        conflictUser.setUsername(DevelopmentDataInitializer.OWNER_USERNAME);
        conflictUser.setStatus(UserStatus.LOCKED); // Causes conflict with expected ACTIVE status
        conflictUser.setPasswordHash(passwordEncoder.encode("TestPassword123!"));
        userRepository.save(conflictUser);

        assertThat(userRepository.count()).isEqualTo(1);
        assertThat(roleRepository.count()).isEqualTo(6);

        // 2. Setup initializer with dev environment emulated
        MockEnvironment mockEnv = new MockEnvironment();
        mockEnv.setActiveProfiles("dev");
        mockEnv.setProperty("aquasuite.config.environment", "dev");
        mockEnv.setProperty("aquasuite.iam.dev-seed.enabled", "true");

        DevelopmentDataInitializer initializer = new DevelopmentDataInitializer(
                roleRepository,
                userRepository,
                passwordEncoder,
                transactionTemplate,
                mockEnv
        );
        initializer.setPasswordSupplier(() -> "TestPassword123!");

        // 3. Execute: exception must be thrown due to owner status conflict
        assertThatThrownBy(initializer::initialize)
                .isInstanceOf(DevelopmentDataInitializer.SeedConflictException.class)
                .hasMessageContaining("non-ACTIVE status");

        // 4. Verify transaction rollback: canonical roles preserved from V2, no uncommitted roles or owner created
        assertThat(roleRepository.count()).isEqualTo(6);
        assertThat(roleRepository.findByRoleNameIgnoreCase("SHOP_OWNER")).isPresent();

        // Conflict user remains unchanged
        assertThat(userRepository.count()).isEqualTo(1);
    }
}
