package com.aquasuite.ims.module.iam.internal.mapper;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringJUnitConfig(MapperTestConfiguration.class)
class RoleMapperTest {

    @Autowired
    private RoleMapper roleMapper;

    @Test
    @DisplayName("toResponse: maps all scalar fields and sorts nested permissions ascending by code")
    void testToResponse_Success() {
        UUID roleId = UUID.randomUUID();
        Permission pZ = new Permission(UUID.randomUUID(), "ZONE_READ", "Read zones");
        Permission pA = new Permission(UUID.randomUUID(), "ACCOUNT_READ", "Read accounts");
        Permission pM = new Permission(UUID.randomUUID(), "MONITOR_READ", "Read monitors");

        Role role = new Role(roleId, "ADMIN", "Administrator role", true, Instant.now(), new HashSet<>(Set.of(pZ, pA, pM)));

        RoleResponse response = roleMapper.toResponse(role);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(roleId);
        assertThat(response.getCode()).isEqualTo("ADMIN");
        assertThat(response.getDescription()).isEqualTo("Administrator role");
        assertThat(response.isActive()).isTrue();
        assertThat(response.getPermissions()).hasSize(3);
        assertThat(response.getPermissions())
                .extracting("code")
                .containsExactly("ACCOUNT_READ", "MONITOR_READ", "ZONE_READ");
    }

    @Test
    @DisplayName("toResponse: preserves null description")
    void testToResponse_NullableDescription() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role(roleId, "GUEST", null, false, Instant.now(), Collections.emptySet());

        RoleResponse response = roleMapper.toResponse(role);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(roleId);
        assertThat(response.getCode()).isEqualTo("GUEST");
        assertThat(response.getDescription()).isNull();
        assertThat(response.isActive()).isFalse();
        assertThat(response.getPermissions()).isEmpty();
    }

    @Test
    @DisplayName("toResponse: null permissions set produces empty list")
    void testToResponse_NullPermissionsSet_ProducesEmptyList() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role(roleId, "USER", "Standard user", true, Instant.now(), null);

        RoleResponse response = roleMapper.toResponse(role);

        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toResponse: empty permissions set produces empty list")
    void testToResponse_EmptyPermissionsSet_ProducesEmptyList() {
        UUID roleId = UUID.randomUUID();
        Role role = new Role(roleId, "USER", "Standard user", true, Instant.now(), Collections.emptySet());

        RoleResponse response = roleMapper.toResponse(role);

        assertThat(response).isNotNull();
        assertThat(response.getPermissions()).isNotNull().isEmpty();
    }

    @Test
    @DisplayName("toResponse: throws NullPointerException on null role")
    void testToResponse_NullInput_ThrowsNullPointerException() {
        assertThatThrownBy(() -> roleMapper.toResponse(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toResponse: throws NullPointerException on null element inside permissions collection")
    void testToResponse_NullPermissionElement_ThrowsNullPointerException() {
        UUID roleId = UUID.randomUUID();
        Set<Permission> permissions = new HashSet<>();
        permissions.add(null);
        Role role = new Role(roleId, "ADMIN", "Administrator", true, Instant.now(), permissions);

        assertThatThrownBy(() -> roleMapper.toResponse(role))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("toResponse: does not mutate role or permission entities")
    void testToResponse_EntityNotMutated() {
        UUID roleId = UUID.randomUUID();
        UUID permId = UUID.randomUUID();
        Permission permission = new Permission(permId, "PERM_A", "Desc A");
        Set<Permission> originalPermissions = new HashSet<>(Set.of(permission));
        Role role = new Role(roleId, "MANAGER", "Manager role", true, Instant.now(), originalPermissions);

        roleMapper.toResponse(role);

        assertThat(role.getRoleId()).isEqualTo(roleId);
        assertThat(role.getRoleName()).isEqualTo("MANAGER");
        assertThat(role.getDescription()).isEqualTo("Manager role");
        assertThat(role.isActive()).isTrue();
        assertThat(role.getPermissions()).containsExactly(permission);
        assertThat(permission.getId()).isEqualTo(permId);
        assertThat(permission.getCode()).isEqualTo("PERM_A");
        assertThat(permission.getDescription()).isEqualTo("Desc A");
    }

    @Test
    @DisplayName("toResponse: returns unmodifiable permissions list")
    void testToResponse_PermissionsListUnmodifiable() {
        UUID roleId = UUID.randomUUID();
        Permission p = new Permission(UUID.randomUUID(), "PERM_A", "Desc A");
        Role role = new Role(roleId, "ROLE_TEST", "Test", true, Instant.now(), Set.of(p));

        RoleResponse response = roleMapper.toResponse(role);

        assertThatThrownBy(() -> response.getPermissions().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
