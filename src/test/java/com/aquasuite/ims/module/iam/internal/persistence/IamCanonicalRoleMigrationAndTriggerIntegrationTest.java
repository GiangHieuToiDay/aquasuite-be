package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IamCanonicalRoleMigrationAndTriggerIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    private static final Map<String, String> EXPECTED_CANONICAL_ROLES = Map.of(
            "SHOP_OWNER", "10000000-0000-4000-8000-000000000001",
            "SALES_STAFF", "10000000-0000-4000-8000-000000000002",
            "ACCOUNTANT", "10000000-0000-4000-8000-000000000003",
            "TECHNICAL_STAFF", "10000000-0000-4000-8000-000000000004",
            "SHIPPER", "10000000-0000-4000-8000-000000000005",
            "CUSTOMER", "10000000-0000-4000-8000-000000000006"
    );

    @AfterEach
    void cleanUp() throws SQLException {
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
    @DisplayName("AC-01, AC-11, AC-15: Canonical roles provisioned with exact literals and active status")
    void testCanonicalRolesProvisionedCorrectly() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT role_id, role_name, description, is_active FROM public.roles WHERE role_name IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER')")) {
            try (ResultSet rs = ps.executeQuery()) {
                Map<String, String> foundRoles = new HashMap<>();
                while (rs.next()) {
                    String roleName = rs.getString("role_name");
                    String roleId = rs.getString("role_id");
                    boolean isActive = rs.getBoolean("is_active");
                    foundRoles.put(roleName, roleId);
                    assertThat(isActive).isTrue();
                }
                assertThat(foundRoles).containsExactlyInAnyOrderEntriesOf(EXPECTED_CANONICAL_ROLES);
            }
        }
    }

    @Test
    @DisplayName("AC-14, AC-29: Trigger prevents direct DELETE of any canonical role")
    void testTriggerPreventsCanonicalRoleDeletion() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            for (String roleName : EXPECTED_CANONICAL_ROLES.keySet()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM public.roles WHERE role_name = ?")) {
                    ps.setString(1, roleName);
                    assertThatThrownBy(ps::executeUpdate)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("Cannot delete canonical role: " + roleName);
                }
            }
        }
    }

    @Test
    @DisplayName("AC-14, AC-29: Trigger prevents direct rename of any canonical role")
    void testTriggerPreventsCanonicalRoleRename() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            for (String roleName : EXPECTED_CANONICAL_ROLES.keySet()) {
                try (PreparedStatement ps = conn.prepareStatement("UPDATE public.roles SET role_name = ? WHERE role_name = ?")) {
                    ps.setString(1, "RENAMED_" + roleName);
                    ps.setString(2, roleName);
                    assertThatThrownBy(ps::executeUpdate)
                            .isInstanceOf(SQLException.class)
                            .hasMessageContaining("Cannot rename canonical role: " + roleName);
                }
            }
        }
    }

    @Test
    @DisplayName("AC-14, AC-29: Trigger allows updating metadata (description, is_active) on canonical roles")
    void testTriggerAllowsCanonicalRoleMetadataUpdate() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement("UPDATE public.roles SET description = 'Mô tả tạm thời' WHERE role_name = 'SHOP_OWNER'")) {
            int updated = ps.executeUpdate();
            assertThat(updated).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Trigger allows deleting and renaming non-canonical roles")
    void testTriggerAllowsNonCanonicalRoleMutation() throws SQLException {
        UUID nonCanonicalId = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO public.roles (role_id, role_name, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, nonCanonicalId);
                ps.setString(2, "TEMP_ROLE");
                ps.setString(3, "Temporary role");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("UPDATE public.roles SET role_name = 'TEMP_ROLE_2' WHERE role_id = ?")) {
                ps.setObject(1, nonCanonicalId);
                int updated = ps.executeUpdate();
                assertThat(updated).isEqualTo(1);
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM public.roles WHERE role_id = ?")) {
                ps.setObject(1, nonCanonicalId);
                int deleted = ps.executeUpdate();
                assertThat(deleted).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("Trigger does not block role_permissions insert and delete for canonical roles")
    void testTriggerDoesNotBlockRolePermissions() throws SQLException {
        UUID permId = UUID.randomUUID();
        UUID roleId = UUID.fromString(EXPECTED_CANONICAL_ROLES.get("SHOP_OWNER"));
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO public.permissions (id, code, description) VALUES (?, 'TEMP_PERM', 'Desc')")) {
                ps.setObject(1, permId);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("INSERT INTO public.role_permissions (role_id, permission_id) VALUES (?, ?)")) {
                ps.setObject(1, roleId);
                ps.setObject(2, permId);
                int inserted = ps.executeUpdate();
                assertThat(inserted).isEqualTo(1);
            }

            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM public.role_permissions WHERE role_id = ? AND permission_id = ?")) {
                ps.setObject(1, roleId);
                ps.setObject(2, permId);
                int deleted = ps.executeUpdate();
                assertThat(deleted).isEqualTo(1);
            }
        }
    }
}
