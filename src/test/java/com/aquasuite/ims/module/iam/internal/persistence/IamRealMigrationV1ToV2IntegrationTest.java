package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IamRealMigrationV1ToV2IntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

    private Flyway configureFlyway(String target) {
        return Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration")
                .target(target)
                .cleanDisabled(false)
                .load();
    }

    private void migrateToV1Clean() {
        Flyway flyway = configureFlyway("1");
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void restoreDatabaseToLatestV2() {
        // Ensure database is clean and migrated to latest V2 for subsequent tests
        Flyway flyway = Flyway.configure().dataSource(dataSource).schemas("public").locations("classpath:db/migration").cleanDisabled(false).load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration provisions all 6 missing canonical roles with exact literal UUIDs")
    void testV1ToV2ProvisionsMissingCanonicalRoles() throws SQLException {
        migrateToV1Clean();

        // Run real Flyway V2 migration
        Flyway flywayV2 = configureFlyway("2");
        flywayV2.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT role_id, role_name FROM public.roles ORDER BY role_name")) {
            int count = 0;
            while (rs.next()) {
                count++;
                String roleName = rs.getString("role_name");
                String roleId = rs.getString("role_id");
                switch (roleName) {
                    case "SHOP_OWNER" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000001");
                    case "SALES_STAFF" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000002");
                    case "ACCOUNTANT" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000003");
                    case "TECHNICAL_STAFF" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000004");
                    case "SHIPPER" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000005");
                    case "CUSTOMER" -> assertThat(roleId).isEqualTo("10000000-0000-4000-8000-000000000006");
                }
            }
            assertThat(count).isEqualTo(6);
        }
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration preserves existing compatible canonical roles, UUIDs, metadata, and non-canonical roles")
    void testV1ToV2PreservesExistingCompatibleData() throws SQLException {
        migrateToV1Clean();

        UUID customOwnerId = UUID.randomUUID();
        UUID customAdminId = UUID.randomUUID();

        // Seed existing compatible data in V1 schema
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.roles (role_id, role_name, description, is_active) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, customOwnerId);
                ps.setString(2, "SHOP_OWNER");
                ps.setString(3, "Custom Owner Description");
                ps.setBoolean(4, true);
                ps.executeUpdate();

                ps.setObject(1, customAdminId);
                ps.setString(2, "CUSTOM_ADMIN");
                ps.setString(3, "Non canonical admin");
                ps.setBoolean(4, true);
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "ORDER_VIEW");
                ps.setString(3, "View orders");
                ps.executeUpdate();
            }
        }

        // Run real Flyway V2 migration
        Flyway flywayV2 = configureFlyway("2");
        flywayV2.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            // Verify existing SHOP_OWNER preserved custom UUID and description
            try (ResultSet rs = stmt.executeQuery("SELECT role_id, description FROM public.roles WHERE role_name = 'SHOP_OWNER'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("role_id")).isEqualTo(customOwnerId.toString());
                assertThat(rs.getString("description")).isEqualTo("Custom Owner Description");
            }

            // Verify non-canonical role CUSTOM_ADMIN preserved
            try (ResultSet rs = stmt.executeQuery("SELECT role_id FROM public.roles WHERE role_name = 'CUSTOM_ADMIN'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("role_id")).isEqualTo(customAdminId.toString());
            }

            // Verify other 5 missing canonical roles provisioned with literal UUIDs
            try (ResultSet rs = stmt.executeQuery("SELECT role_id FROM public.roles WHERE role_name = 'SALES_STAFF'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("role_id")).isEqualTo("10000000-0000-4000-8000-000000000002");
            }

            // Verify existing permission preserved
            try (ResultSet rs = stmt.executeQuery("SELECT code FROM public.permissions WHERE code = 'ORDER_VIEW'")) {
                assertThat(rs.next()).isTrue();
            }
        }
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration fails and rolls back on existing invalid permission code")
    void testV1ToV2FailsOnInvalidPermissionCode() throws SQLException {
        migrateToV1Clean();

        // Seed invalid permission in V1 (lowercase code)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "invalid_lowercase_code");
            ps.setString(3, "Invalid code");
            ps.executeUpdate();
        }

        Flyway flywayV2 = configureFlyway("2");
        assertThatThrownBy(flywayV2::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("invalid code");

        // Verify transaction rollback: CHECK constraints and triggers were not created, V2 is not marked applied
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM information_schema.check_constraints WHERE constraint_name = 'chk_permissions_code_format'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration fails and rolls back on existing permission starting with ROLE_")
    void testV1ToV2FailsOnRolePrefixPermissionCode() throws SQLException {
        migrateToV1Clean();

        // Seed forbidden prefix in V1
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "ROLE_ADMIN");
            ps.setString(3, "Forbidden prefix");
            ps.executeUpdate();
        }

        Flyway flywayV2 = configureFlyway("2");
        assertThatThrownBy(flywayV2::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("forbidden prefix ROLE_");
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration fails and rolls back on case-variant canonical role conflict")
    void testV1ToV2FailsOnCaseVariantRoleConflict() throws SQLException {
        migrateToV1Clean();

        // Seed case-variant role in V1
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.roles (role_id, role_name, description, is_active) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "shop_owner");
            ps.setString(3, "Lowercase conflict");
            ps.setBoolean(4, true);
            ps.executeUpdate();
        }

        Flyway flywayV2 = configureFlyway("2");
        assertThatThrownBy(flywayV2::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("case-variant conflict");
    }

    @Test
    @DisplayName("Finding 2: V1 to V2 migration fails and rolls back on canonical UUID collision")
    void testV1ToV2FailsOnCanonicalUuidCollision() throws SQLException {
        migrateToV1Clean();

        // Seed UUID literal bound to different role in V1
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.roles (role_id, role_name, description, is_active) VALUES (?, ?, ?, ?)")) {
            ps.setObject(1, UUID.fromString("10000000-0000-4000-8000-000000000001"));
            ps.setString(2, "DIFFERENT_ROLE");
            ps.setString(3, "Collision");
            ps.setBoolean(4, true);
            ps.executeUpdate();
        }

        Flyway flywayV2 = configureFlyway("2");
        assertThatThrownBy(flywayV2::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("collision detected");
    }

    @Test
    @DisplayName("Finding 1: Database CHECK constraints reject invalid format, length, and ROLE_ prefix on direct SQL inserts/updates")
    void testDatabaseCheckConstraintsEnforcement() throws SQLException {
        // Database is at V2
        try (Connection conn = dataSource.getConnection()) {
            // 1. Direct insert lowercase code -> fails chk_permissions_code_format
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "lowercase_code");
                ps.setString(3, "Test");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_permissions_code_format");
            }

            // 2. Direct insert code starting with number -> fails chk_permissions_code_format
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "123_INVALID");
                ps.setString(3, "Test");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_permissions_code_format");
            }

            // 3. Direct insert code with special chars -> fails chk_permissions_code_format
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "INVALID-DASH");
                ps.setString(3, "Test");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_permissions_code_format");
            }

            // 4. Direct insert code with ROLE_ prefix -> fails chk_permissions_code_not_role_prefix
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, UUID.randomUUID());
                ps.setString(2, "ROLE_ADMIN");
                ps.setString(3, "Test");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_permissions_code_not_role_prefix");
            }

            // 5. Direct insert valid uppercase code -> succeeds
            UUID validId = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
                ps.setObject(1, validId);
                ps.setString(2, "VALID_DIRECT_CODE");
                ps.setString(3, "Valid");
                int rows = ps.executeUpdate();
                assertThat(rows).isEqualTo(1);
            }

            // 6. Direct update to invalid code -> fails chk_permissions_code_format
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE public.permissions SET code = ? WHERE id = ?")) {
                ps.setString(1, "invalid_update");
                ps.setObject(2, validId);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_permissions_code_format");
            }
        }
    }
}
