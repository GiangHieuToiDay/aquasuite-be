package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.IamPermissionService;
import com.aquasuite.ims.module.iam.internal.service.IamRoleService;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.GrantedAuthority;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IamCurrentAuthorityResolutionIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private CurrentAuthoritiesService currentAuthoritiesService;

    @Autowired
    private IamRoleService roleService;

    @Autowired
    private IamPermissionService permissionService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions");
            stmt.executeUpdate("DELETE FROM public.user_roles");
            stmt.executeUpdate("DELETE FROM public.permissions");
            stmt.executeUpdate("DELETE FROM public.roles WHERE role_name NOT IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER')");
            stmt.executeUpdate("DELETE FROM public.users");
        }
    }

    @Test
    @DisplayName("AC-10, AC-19, AC-20: Assign/unassign permission takes effect on next request authority resolution without re-login")
    void testAuthorityResolutionImmediatelyReflectsPermissionAssignment() {
        Role accountantRole = roleRepository.findByRoleNameIgnoreCase("ACCOUNTANT").orElseThrow();

        User accountantUser = new User();
        accountantUser.setId(UUID.randomUUID());
        accountantUser.setUsername("accountant_bob");
        accountantUser.setEmail("bob@aquasuite.local");
        accountantUser.setPasswordHash("hash123");
        accountantUser.setStatus(UserStatus.ACTIVE);
        accountantUser.setRoles(new HashSet<>(Set.of(accountantRole)));
        userRepository.save(accountantUser);

        // 1. Initial resolution: user has ROLE_ACCOUNTANT, no custom permissions
        User loadedUser = currentAuthoritiesService.loadCurrentUser(accountantUser.getId()).orElseThrow();
        Collection<GrantedAuthority> initialAuthorities = currentAuthoritiesService.resolveAuthorities(loadedUser.getRoles());
        Set<String> authStrings = initialAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(authStrings).contains("ROLE_ACCOUNTANT");
        assertThat(authStrings).doesNotContain("FINANCIAL_AUDIT");

        // 2. Create permission and assign to ACCOUNTANT role
        Permission perm = new Permission(UUID.randomUUID(), "FINANCIAL_AUDIT", "Audit finances");
        permissionRepository.saveAndFlush(perm);
        roleService.assignPermissionToRole(accountantRole.getRoleId(), perm.getId());

        // 3. Next request authority resolution: immediately reflects FINANCIAL_AUDIT without re-login
        User reloadedUser = currentAuthoritiesService.loadCurrentUser(accountantUser.getId()).orElseThrow();
        Collection<GrantedAuthority> updatedAuthorities = currentAuthoritiesService.resolveAuthorities(reloadedUser.getRoles());
        Set<String> updatedAuthStrings = updatedAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(updatedAuthStrings).contains("ROLE_ACCOUNTANT", "FINANCIAL_AUDIT");

        // 4. Unassign permission from ACCOUNTANT role
        roleService.unassignPermissionFromRole(accountantRole.getRoleId(), perm.getId());

        // 5. Subsequent request authority resolution: immediately reflects removal
        User subsequentUser = currentAuthoritiesService.loadCurrentUser(accountantUser.getId()).orElseThrow();
        Collection<GrantedAuthority> subsequentAuthorities = currentAuthoritiesService.resolveAuthorities(subsequentUser.getRoles());
        Set<String> subsequentAuthStrings = subsequentAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(subsequentAuthStrings).contains("ROLE_ACCOUNTANT");
        assertThat(subsequentAuthStrings).doesNotContain("FINANCIAL_AUDIT");
    }
}
