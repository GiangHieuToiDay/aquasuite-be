package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit test verifying privilege boundaries and authority containment:
 * - A user's authorities are strictly derived from DB roles/permissions.
 * - Non-admin users do not receive admin roles/permissions.
 * - Changing roles in the DB takes immediate effect on authority resolution.
 */
@ExtendWith(MockitoExtension.class)
class PrivilegeBoundaryIntegrationTest {

    @Mock
    private UserRepository userRepository;

    private CurrentAuthoritiesService currentAuthoritiesService;

    @BeforeEach
    void setUp() {
        currentAuthoritiesService = new CurrentAuthoritiesService(userRepository);
    }

    @Test
    @DisplayName("AC-10: Non-admin user cannot possess ADMIN authorities or elevate own permissions")
    void nonAdminUserCannotHaveAdminAuthorities() {
        UUID regularUserId = UUID.randomUUID();

        Role staffRole = new Role(UUID.randomUUID(), "ROLE_STAFF", "Staff", true, null, Set.of());
        User staffUser = new User();
        staffUser.setId(regularUserId);
        staffUser.setUsername("staff_user");
        staffUser.setStatus(UserStatus.ACTIVE);
        staffUser.setRoles(Set.of(staffRole));

        when(userRepository.findWithRolesAndPermissionsById(regularUserId)).thenReturn(Optional.of(staffUser));

        User loadedUser = currentAuthoritiesService.loadCurrentUser(regularUserId).orElseThrow();
        Collection<GrantedAuthority> authorities = currentAuthoritiesService.resolveAuthorities(loadedUser.getRoles());
        Set<String> authNames = authorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());

        assertThat(authNames).contains("ROLE_STAFF");
        assertThat(authNames).doesNotContain("ROLE_ADMIN", "ROLE_SUPER_ADMIN");
    }

    @Test
    @DisplayName("AC-10: Authority resolution reflects DB permission changes immediately")
    void authorityResolutionReflectsDbChangesImmediately() {
        UUID userId = UUID.randomUUID();

        Role viewerRole = new Role(UUID.randomUUID(), "ROLE_VIEWER", "Viewer", true, null, Set.of());
        User user = new User();
        user.setId(userId);
        user.setUsername("viewer_user");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(viewerRole));

        when(userRepository.findWithRolesAndPermissionsById(userId)).thenReturn(Optional.of(user));

        User loadedUser = currentAuthoritiesService.loadCurrentUser(userId).orElseThrow();
        Collection<GrantedAuthority> initialAuthorities = currentAuthoritiesService.resolveAuthorities(loadedUser.getRoles());
        Set<String> initialNames = initialAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(initialNames).containsExactly("ROLE_VIEWER");

        // Simulate role change in DB
        Role newRole = new Role(UUID.randomUUID(), "ROLE_OPERATOR", "Operator", true, null, Set.of());
        user.setRoles(Set.of(newRole));

        User updatedUser = currentAuthoritiesService.loadCurrentUser(userId).orElseThrow();
        Collection<GrantedAuthority> updatedAuthorities = currentAuthoritiesService.resolveAuthorities(updatedUser.getRoles());
        Set<String> updatedNames = updatedAuthorities.stream().map(GrantedAuthority::getAuthority).collect(Collectors.toSet());
        assertThat(updatedNames).containsExactly("ROLE_OPERATOR");
        assertThat(updatedNames).doesNotContain("ROLE_VIEWER");
    }
}
