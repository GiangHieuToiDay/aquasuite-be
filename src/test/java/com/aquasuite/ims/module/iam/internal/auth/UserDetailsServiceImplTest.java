package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.auth.security.UserDetailsServiceImpl;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Permission;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.authorization.service.impl.CurrentAuthoritiesService;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserDetailsServiceImplTest {

    @Mock
    private UserRepository userRepository;

    private CurrentAuthoritiesService currentAuthoritiesService;
    private UserDetailsServiceImpl userDetailsService;

    @BeforeEach
    void setUp() {
        currentAuthoritiesService = new CurrentAuthoritiesService(userRepository);
        userDetailsService = new UserDetailsServiceImpl(userRepository, currentAuthoritiesService);
    }

    @Test
    @DisplayName("Nạp user thành công với quyền hạn và trạng thái enabled=true khi user ACTIVE")
    void shouldLoadActiveUserSuccessfully() {
        String email = "alice@aquasuite.com";
        Permission p = new Permission(UUID.randomUUID(), "iam:user:view", "View users");
        Role r = new Role(UUID.randomUUID(), "ROLE_ADMIN", "Admin role", true, null, Set.of(p));

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("alice");
        user.setEmail(email);
        user.setPasswordHash("$2a$12$hash...");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(Set.of(r));

        when(userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(eq(email)))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername(email);

        assertThat(details).isNotNull();
        assertThat(details.getUsername()).isEqualTo(email);
        assertThat(details.getPassword()).isEqualTo("$2a$12$hash...");
        assertThat(details.isEnabled()).isTrue();
        assertThat(details.getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "iam:user:view");
    }

    @Test
    @DisplayName("User có trạng thái khác ACTIVE (ví dụ PENDING_ACTIVATION) thì enabled=false")
    void shouldMarkDisabledWhenStatusNotActive() {
        String email = "bob@aquasuite.com";
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("bob");
        user.setEmail(email);
        user.setPasswordHash("$2a$12$hash...");
        user.setStatus(UserStatus.PENDING_ACTIVATION);
        user.setRoles(Set.of());

        when(userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(eq(email)))
                .thenReturn(Optional.of(user));

        UserDetails details = userDetailsService.loadUserByUsername(email);

        assertThat(details).isNotNull();
        assertThat(details.isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Không tìm thấy user theo email ném UsernameNotFoundException")
    void shouldThrowWhenUserNotFound() {
        String email = "unknown@aquasuite.com";
        when(userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(eq(email)))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> userDetailsService.loadUserByUsername(email))
                .isInstanceOf(UsernameNotFoundException.class)
                .hasMessageContaining("User not found");
    }
}
