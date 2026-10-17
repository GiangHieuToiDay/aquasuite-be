package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.module.iam.internal.entity.PermissionEntity;
import com.aquasuite.ims.module.iam.internal.entity.RoleEntity;
import com.aquasuite.ims.module.iam.internal.entity.UserEntity;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IamRepositoryIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions");
            stmt.executeUpdate("DELETE FROM public.user_roles");
            stmt.executeUpdate("DELETE FROM public.permissions");
            stmt.executeUpdate("DELETE FROM public.roles");
            stmt.executeUpdate("DELETE FROM public.users");
        }
    }

    @Test
    @DisplayName("CP3: UserRepository case-insensitive lookups and existence checks")
    void testUserRepositoryLookups() {
        UserEntity user = new UserEntity();
        user.setUsername("AlexSmith");
        user.setEmail("Alex.Smith@Test.com");
        user.setPasswordHash("hashed_pwd");
        user = userRepository.saveAndFlush(user);

        Optional<UserEntity> byUsername = userRepository.findByUsernameIgnoreCase("alexsmith");
        assertThat(byUsername).isPresent();
        assertThat(byUsername.get().getId()).isEqualTo(user.getId());

        Optional<UserEntity> byEmail = userRepository.findByEmailIgnoreCase("alex.smith@test.com");
        assertThat(byEmail).isPresent();
        assertThat(byEmail.get().getId()).isEqualTo(user.getId());

        assertThat(userRepository.existsByUsernameIgnoreCase("ALEXSMITH")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("ALEX.SMITH@TEST.COM")).isTrue();
        assertThat(userRepository.existsByUsernameIgnoreCase("nonexistent")).isFalse();
        assertThat(userRepository.existsByEmailIgnoreCase("nonexistent@test.com")).isFalse();
    }

    @Test
    @DisplayName("CP3: RoleRepository case-insensitive lookups and existence checks")
    void testRoleRepositoryLookups() {
        RoleEntity role = new RoleEntity();
        role.setRoleName("SuperAdmin");
        role.setDescription("Highest privileges");
        role.setActive(true);
        role = roleRepository.saveAndFlush(role);

        Optional<RoleEntity> byName = roleRepository.findByRoleNameIgnoreCase("superadmin");
        assertThat(byName).isPresent();
        assertThat(byName.get().getRoleId()).isEqualTo(role.getRoleId());

        assertThat(roleRepository.existsByRoleNameIgnoreCase("SUPERADMIN")).isTrue();
        assertThat(roleRepository.existsByRoleNameIgnoreCase("OPERATOR")).isFalse();
    }

    @Test
    @DisplayName("CP3: PermissionRepository case-insensitive lookups and existence checks")
    void testPermissionRepositoryLookups() {
        PermissionEntity perm = new PermissionEntity();
        perm.setCode("ORDER_VIEW");
        perm.setDescription("View order list");
        perm = permissionRepository.saveAndFlush(perm);

        Optional<PermissionEntity> byCode = permissionRepository.findByCodeIgnoreCase("order_view");
        assertThat(byCode).isPresent();
        assertThat(byCode.get().getId()).isEqualTo(perm.getId());

        assertThat(permissionRepository.existsByCodeIgnoreCase("ORDER_VIEW")).isTrue();
        assertThat(permissionRepository.existsByCodeIgnoreCase("ORDER_EDIT")).isFalse();
    }

    @Test
    @DisplayName("CP3, AC-07: Three main repositories manage ManyToMany associations and traversals")
    void testRepositoriesManageManyToManyAssociations() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        tx.execute(status -> {
            PermissionEntity perm = new PermissionEntity();
            perm.setCode("REPORT_EXPORT");
            perm = permissionRepository.save(perm);

            RoleEntity role = new RoleEntity();
            role.setRoleName("ANALYST");
            role.getPermissions().add(perm);
            role = roleRepository.save(role);

            UserEntity user = new UserEntity();
            user.setUsername("data_analyst");
            user.setEmail("analyst@test.com");
            user.setPasswordHash("h");
            user.getRoles().add(role);
            user = userRepository.save(user);

            // Traverse from user
            UserEntity reloadedUser = userRepository.findById(user.getId()).orElseThrow();
            assertThat(reloadedUser.getRoles()).hasSize(1);
            RoleEntity fetchedRole = reloadedUser.getRoles().iterator().next();
            assertThat(fetchedRole.getRoleName()).isEqualTo("ANALYST");
            assertThat(fetchedRole.getPermissions()).hasSize(1);
            assertThat(fetchedRole.getPermissions().iterator().next().getCode()).isEqualTo("REPORT_EXPORT");

            return null;
        });
    }
}
