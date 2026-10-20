package com.aquasuite.ims.module.iam.internal.auth;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Permission;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.authorization.service.impl.CurrentAuthoritiesService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class AuthorityMappingTest {

    private CurrentAuthoritiesService service;
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository = Mockito.mock(UserRepository.class);
        service = new CurrentAuthoritiesService(userRepository);
    }

    @Test
    @DisplayName("Chuẩn hóa roleName: trim, uppercase, thêm đúng một prefix ROLE_")
    void shouldNormalizeRoleNamesCorrectly() {
        Role role1 = new Role(UUID.randomUUID(), "  admin  ", "Admin", true, null, Set.of());
        Role role2 = new Role(UUID.randomUUID(), "role_operator", "Operator", true, null, Set.of());
        Role role3 = new Role(UUID.randomUUID(), "ROLE_SUPERVISOR", "Supervisor", true, null, Set.of());

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(role1, role2, role3));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authStrings).containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_OPERATOR", "ROLE_SUPERVISOR");
    }

    @Test
    @DisplayName("Loại bỏ role không active (isActive = false)")
    void shouldExcludeInactiveRoles() {
        Permission perm = new Permission(UUID.randomUUID(), "user:read", "Read users");
        Role inactiveRole = new Role(UUID.randomUUID(), "admin", "Admin", false, null, Set.of(perm));

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(inactiveRole));

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Loại bỏ role invalid: null, blank, chỉ ROLE_, hoặc ký tự không hợp lệ")
    void shouldExcludeInvalidRoles() {
        Role nullRole = new Role(UUID.randomUUID(), null, "Null", true, null, Set.of());
        Role blankRole = new Role(UUID.randomUUID(), "   ", "Blank", true, null, Set.of());
        Role onlyPrefixRole = new Role(UUID.randomUUID(), "ROLE_", "Prefix only", true, null, Set.of());
        Role specialCharRole = new Role(UUID.randomUUID(), "admin-manager", "Dash", true, null, Set.of());

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(nullRole, blankRole, onlyPrefixRole, specialCharRole));

        assertThat(authorities).isEmpty();
    }

    @Test
    @DisplayName("Phát hiện collision: hai role ID khác nhau chuẩn hóa thành cùng role authority -> hủy cả hai")
    void shouldDisqualifyCollidingRoles() {
        Permission permA = new Permission(UUID.randomUUID(), "perm:a", "A");
        Permission permB = new Permission(UUID.randomUUID(), "perm:b", "B");
        Role roleA = new Role(UUID.randomUUID(), "admin", "Lower", true, null, Set.of(permA));
        Role roleB = new Role(UUID.randomUUID(), "ROLE_ADMIN", "Prefixed", true, null, Set.of(permB));

        Role roleValid = new Role(UUID.randomUUID(), "viewer", "Viewer", true, null, Set.of());

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(roleA, roleB, roleValid));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        // Colliding roles (ROLE_ADMIN) and their permissions (perm:a, perm:b) must not be granted
        assertThat(authStrings).doesNotContain("ROLE_ADMIN", "perm:a", "perm:b");
        assertThat(authStrings).containsExactly("ROLE_VIEWER");
    }

    @Test
    @DisplayName("Bảo toàn nguyên vẹn code của permission hợp lệ")
    void shouldPreserveExactPermissionCode() {
        Permission p1 = new Permission(UUID.randomUUID(), "order:Read_Detailed", "Desc");
        Permission p2 = new Permission(UUID.randomUUID(), "DEVICE:WRITE", "Desc");
        Role role = new Role(UUID.randomUUID(), "manager", "Manager", true, null, Set.of(p1, p2));

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(role));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authStrings).contains("ROLE_MANAGER", "order:Read_Detailed", "DEVICE:WRITE");
    }

    @Test
    @DisplayName("Permission code thuộc namespace ROLE_ (không phân biệt hoa thường) bị loại bỏ, không nâng thành role")
    void shouldExcludePermissionsInRoleNamespace() {
        Permission illegalPerm1 = new Permission(UUID.randomUUID(), "ROLE_ADMIN", "Illegal");
        Permission illegalPerm2 = new Permission(UUID.randomUUID(), "role_supervisor", "Illegal");
        Permission illegalPerm3 = new Permission(UUID.randomUUID(), "  ROLE_OPERATOR ", "Illegal");
        Permission validPerm = new Permission(UUID.randomUUID(), "user:view", "Valid");

        Role role = new Role(UUID.randomUUID(), "clerk", "Clerk", true, null,
                Set.of(illegalPerm1, illegalPerm2, illegalPerm3, validPerm));

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(role));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authStrings).contains("ROLE_CLERK", "user:view");
        assertThat(authStrings).doesNotContain("ROLE_ADMIN", "role_supervisor", "ROLE_OPERATOR");
    }

    @Test
    @DisplayName("resolveActiveRoleNames trả về danh sách role names sạch cho login response")
    void shouldResolveActiveRoleNamesForLoginResponse() {
        Role role1 = new Role(UUID.randomUUID(), "admin", "Admin", true, null, Set.of());
        Role role2 = new Role(UUID.randomUUID(), "ROLE_MANAGER", "Manager", true, null, Set.of());
        Role role3 = new Role(UUID.randomUUID(), "inactive_role", "Inactive", false, null, Set.of());

        List<String> roleNames = service.resolveActiveRoleNames(Set.of(role1, role2, role3));

        assertThat(roleNames).containsExactly("ADMIN", "MANAGER");
    }

    @Test
    @DisplayName("Tất cả 6 role chuẩn FEATURE-009 chuẩn hóa thành ROLE_<NAME> và resolveActiveRoleNames sạch")
    void shouldMapAllSixCanonicalRolesCorrectly() {
        Role shopOwner = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null, Set.of());
        Role salesStaff = new Role(UUID.randomUUID(), "SALES_STAFF", "Nhân viên bán hàng", true, null, Set.of());
        Role accountant = new Role(UUID.randomUUID(), "ACCOUNTANT", "Kế toán", true, null, Set.of());
        Role techStaff = new Role(UUID.randomUUID(), "TECHNICAL_STAFF", "Nhân viên kỹ thuật", true, null, Set.of());
        Role shipper = new Role(UUID.randomUUID(), "SHIPPER", "Nhân viên giao hàng", true, null, Set.of());
        Role customer = new Role(UUID.randomUUID(), "CUSTOMER", "Khách hàng", true, null, Set.of());

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(
                shopOwner, salesStaff, accountant, techStaff, shipper, customer
        ));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authStrings).containsExactlyInAnyOrder(
                "ROLE_SHOP_OWNER",
                "ROLE_SALES_STAFF",
                "ROLE_ACCOUNTANT",
                "ROLE_TECHNICAL_STAFF",
                "ROLE_SHIPPER",
                "ROLE_CUSTOMER"
        );

        List<String> activeRoles = service.resolveActiveRoleNames(Set.of(
                shopOwner, salesStaff, accountant, techStaff, shipper, customer
        ));
        assertThat(activeRoles).containsExactlyInAnyOrder(
                "SHOP_OWNER",
                "SALES_STAFF",
                "ACCOUNTANT",
                "TECHNICAL_STAFF",
                "SHIPPER",
                "CUSTOMER"
        );
    }

    @Test
    @DisplayName("Uppercase permission passthrough và ngăn chặn leo quyền từ ROLE_ namespace permission")
    void shouldPassthroughUppercasePermissionsAndDisqualifyRoleNamespacePermissions() {
        Permission validUpperPerm = new Permission(UUID.randomUUID(), "USER_READ", "Chủ cửa hàng xem thông tin nhân viên");
        Permission roleNamespacePerm = new Permission(UUID.randomUUID(), "ROLE_SHOP_OWNER", "Trùng namespace ROLE");

        Role ownerRole = new Role(UUID.randomUUID(), "SHOP_OWNER", "Chủ cửa hàng", true, null,
                Set.of(validUpperPerm, roleNamespacePerm));

        Collection<GrantedAuthority> authorities = service.resolveAuthorities(Set.of(ownerRole));

        List<String> authStrings = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        assertThat(authStrings).contains("ROLE_SHOP_OWNER", "USER_READ");
        // Must NOT contain ROLE_SHOP_OWNER as a permission authority or duplicate authority
        assertThat(authStrings.stream().filter(s -> s.equals("ROLE_SHOP_OWNER")).count()).isEqualTo(1);
    }
}
