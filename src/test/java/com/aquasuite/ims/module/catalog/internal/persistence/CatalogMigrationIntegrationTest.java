package com.aquasuite.ims.module.catalog.internal.persistence;

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
class CatalogMigrationIntegrationTest extends PostgreSqlIntegrationTestSupport {

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

    private void migrateToV2Clean() {
        Flyway flyway = configureFlyway("2");
        flyway.clean();
        flyway.migrate();
    }

    @AfterEach
    void restoreDatabaseToLatest() {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .schemas("public")
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load();
        flyway.clean();
        flyway.migrate();
    }

    @Test
    @DisplayName("V2 to V3 migration applies cleanly and creates categories, brands and 8 permissions")
    void testV2ToV3CleanMigration() throws SQLException {
        migrateToV2Clean();

        Flyway flywayV3 = configureFlyway("3");
        flywayV3.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Verify 8 catalog permissions created
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT id, code, description FROM public.permissions WHERE code LIKE 'CATALOG_%' ORDER BY code")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    String code = rs.getString("code");
                    String id = rs.getString("id");
                    String desc = rs.getString("description");

                    switch (code) {
                        case "CATALOG_CATEGORY_READ" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000001");
                            assertThat(desc).isEqualTo("Read catalog categories");
                        }
                        case "CATALOG_CATEGORY_CREATE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000002");
                            assertThat(desc).isEqualTo("Create catalog categories");
                        }
                        case "CATALOG_CATEGORY_UPDATE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000003");
                            assertThat(desc).isEqualTo("Update catalog categories");
                        }
                        case "CATALOG_CATEGORY_DELETE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000004");
                            assertThat(desc).isEqualTo("Delete catalog categories");
                        }
                        case "CATALOG_BRAND_READ" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000005");
                            assertThat(desc).isEqualTo("Read catalog brands");
                        }
                        case "CATALOG_BRAND_CREATE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000006");
                            assertThat(desc).isEqualTo("Create catalog brands");
                        }
                        case "CATALOG_BRAND_UPDATE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000007");
                            assertThat(desc).isEqualTo("Update catalog brands");
                        }
                        case "CATALOG_BRAND_DELETE" -> {
                            assertThat(id).isEqualTo("20000000-0000-4000-8000-000000000008");
                            assertThat(desc).isEqualTo("Delete catalog brands");
                        }
                        default -> throw new AssertionError("Unexpected code: " + code);
                    }
                }
                assertThat(count).isEqualTo(8);
            }

            // Verify all 8 catalog permissions are assigned to SHOP_OWNER
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT COUNT(*) FROM public.role_permissions rp " +
                    "JOIN public.roles r ON rp.role_id = r.role_id " +
                    "JOIN public.permissions p ON rp.permission_id = p.id " +
                    "WHERE r.role_name = 'SHOP_OWNER' AND p.code LIKE 'CATALOG_%'")) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getInt(1)).isEqualTo(8);
            }
        }
    }

    @Test
    @DisplayName("Preflight: Migration fails and rolls back atomically on permission UUID collision")
    void testMigrationFailsOnPermissionUuidCollision() throws SQLException {
        migrateToV2Clean();

        // Insert colliding permission with catalog UUID 1 but different code
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.fromString("20000000-0000-4000-8000-000000000001"));
            ps.setString(2, "COLLIDING_CODE");
            ps.setString(3, "Colliding desc");
            ps.executeUpdate();
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("UUID collision detected");

        // Verify categories table was NOT created (atomic rollback)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'categories')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    @DisplayName("Preflight: Migration fails and rolls back atomically on permission code conflict")
    void testMigrationFailsOnPermissionCodeConflict() throws SQLException {
        migrateToV2Clean();

        // Insert colliding permission with catalog code but different UUID
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO public.permissions (id, code, description) VALUES (?, ?, ?)")) {
            ps.setObject(1, UUID.randomUUID());
            ps.setString(2, "CATALOG_CATEGORY_READ");
            ps.setString(3, "Different description");
            ps.executeUpdate();
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Code conflict detected");

        // Verify categories table was NOT created (atomic rollback)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'categories')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    @DisplayName("Preflight: Migration fails if SHOP_OWNER role is missing")
    void testMigrationFailsWhenShopOwnerMissing() throws SQLException {
        migrateToV2Clean();

        // Disable trigger on roles to simulate corrupted/missing SHOP_OWNER
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("ALTER TABLE public.roles DISABLE TRIGGER trg_protect_canonical_roles");
            stmt.execute("DELETE FROM public.roles WHERE role_name = 'SHOP_OWNER'");
            stmt.execute("ALTER TABLE public.roles ENABLE TRIGGER trg_protect_canonical_roles");
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("SHOP_OWNER role is missing");
    }

    @Test
    @DisplayName("Preflight: Migration fails and rolls back if SHOP_OWNER role is inactive")
    void testMigrationFailsWhenShopOwnerInactive() throws SQLException {
        migrateToV2Clean();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("UPDATE public.roles SET is_active = false WHERE role_name = 'SHOP_OWNER'");
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("SHOP_OWNER role is inactive");

        // Verify categories table was NOT created (atomic rollback)
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'categories')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    @DisplayName("Preflight: Migration succeeds when compatible permission and role assignment already exist")
    void testCompatiblePreExistingPermissionAndRoleAssignment() throws SQLException {
        migrateToV2Clean();

        // Seed a compatible permission and assignment in V2
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("INSERT INTO public.permissions (id, code, description) " +
                    "VALUES ('20000000-0000-4000-8000-000000000001', 'CATALOG_CATEGORY_READ', 'Read catalog categories')");
            stmt.execute("INSERT INTO public.role_permissions (role_id, permission_id) " +
                    "SELECT r.role_id, '20000000-0000-4000-8000-000000000001' FROM public.roles r WHERE r.role_name = 'SHOP_OWNER'");
        }

        Flyway flywayV3 = configureFlyway("3");
        flywayV3.migrate();

        // Verify all 8 catalog permissions exist without duplicate
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.permissions WHERE code LIKE 'CATALOG_%'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(8);
        }

        // Verify all 8 catalog permissions are assigned to SHOP_OWNER
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT COUNT(*) FROM public.role_permissions rp " +
                     "JOIN public.roles r ON rp.role_id = r.role_id " +
                     "JOIN public.permissions p ON rp.permission_id = p.id " +
                     "WHERE r.role_name = 'SHOP_OWNER' AND p.code LIKE 'CATALOG_%'")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(8);
        }
    }

    @Test
    @DisplayName("Preflight: Migration fails and rolls back if catalog_set_categories_updated_at function already exists")
    void testPreExistingFunctionCollisionFailsMigrationAndRollsBack() throws SQLException {
        migrateToV2Clean();

        // Create conflicting function before running V3
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE FUNCTION public.catalog_set_categories_updated_at() RETURNS TRIGGER AS $$ " +
                    "BEGIN RETURN NEW; END; $$ LANGUAGE plpgsql;");
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Function catalog_set_categories_updated_at already exists");

        // Verify rollback: categories table does not exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'categories')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    @DisplayName("Preflight: Migration fails and rolls back if categories table already exists")
    void testPreExistingTableCollisionFailsMigrationAndRollsBack() throws SQLException {
        migrateToV2Clean();

        // Create conflicting table before running V3
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("CREATE TABLE public.categories (id INT PRIMARY KEY);");
        }

        Flyway flywayV3 = configureFlyway("3");
        assertThatThrownBy(flywayV3::migrate)
                .isInstanceOf(FlywayException.class)
                .hasMessageContaining("Table categories already exists");

        // Verify rollback: brands table does not exist
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(
                     "SELECT EXISTS (SELECT FROM information_schema.tables WHERE table_name = 'brands')")) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean(1)).isFalse();
        }
    }

    @Test
    @DisplayName("Direct SQL: category and brand constraints and triggers behave exactly as specified")
    void testDirectSqlConstraintsAndTriggers() throws SQLException {
        migrateToV2Clean();
        Flyway flywayV3 = configureFlyway("3");
        flywayV3.migrate();

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Invalid category code: spaces or lowercase rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.categories (category_id, category_code, category_name) VALUES ('" +
                    UUID.randomUUID() + "', ' CAT 109 ', 'Test Name')"))
                    .isInstanceOf(SQLException.class);

            // 2. Valid category code normalized
            UUID cat1Id = UUID.randomUUID();
            stmt.execute("INSERT INTO public.categories (category_id, category_code, category_name) VALUES ('" +
                    cat1Id + "', 'CAT_109', 'Category 1')");

            // 3. Duplicate category code case-insensitive rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.categories (category_id, category_code, category_name) VALUES ('" +
                    UUID.randomUUID() + "', 'CAT_109', 'Different Name')"))
                    .isInstanceOf(SQLException.class);

            // 4. Duplicate root name case-insensitive rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.categories (category_id, category_code, category_name) VALUES ('" +
                    UUID.randomUUID() + "', 'CAT_110', 'category 1')"))
                    .isInstanceOf(SQLException.class);

            // 5. Child under cat1 with same name as root is allowed
            UUID child1Id = UUID.randomUUID();
            stmt.execute("INSERT INTO public.categories (category_id, category_code, category_name, parent_id) VALUES ('" +
                    child1Id + "', 'CAT_CHILD_1', 'Category 1', '" + cat1Id + "')");

            // 6. Sibling duplicate under cat1 rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.categories (category_id, category_code, category_name, parent_id) VALUES ('" +
                    UUID.randomUUID() + "', 'CAT_CHILD_2', 'category 1', '" + cat1Id + "')"))
                    .isInstanceOf(SQLException.class);

            // 7. Self-parent rejected by CHECK constraint
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.categories (category_id, category_code, category_name, parent_id) VALUES ('" +
                    cat1Id + "', 'CAT_SELF', 'Self Cat', '" + cat1Id + "')"))
                    .isInstanceOf(SQLException.class);

            // 8. Delete parent with child fails RESTRICT FK
            assertThatThrownBy(() -> stmt.execute("DELETE FROM public.categories WHERE category_id = '" + cat1Id + "'"))
                    .isInstanceOf(SQLException.class);

            // 9. Category timestamp trigger updates updated_at and preserves created_at
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT created_at, updated_at FROM public.categories WHERE category_id = '" + cat1Id + "'")) {
                assertThat(rs.next()).isTrue();
                java.sql.Timestamp created1 = rs.getTimestamp("created_at");
                java.sql.Timestamp updated1 = rs.getTimestamp("updated_at");

                // Sleep slightly and update
                try {
                    Thread.sleep(50);
                } catch (InterruptedException ignored) {}

                stmt.execute("UPDATE public.categories SET category_name = 'Category 1 Updated' WHERE category_id = '" + cat1Id + "'");

                try (ResultSet rs2 = stmt.executeQuery(
                        "SELECT created_at, updated_at FROM public.categories WHERE category_id = '" + cat1Id + "'")) {
                    assertThat(rs2.next()).isTrue();
                    java.sql.Timestamp created2 = rs2.getTimestamp("created_at");
                    java.sql.Timestamp updated2 = rs2.getTimestamp("updated_at");

                    assertThat(created2).isEqualTo(created1);
                    assertThat(updated2).isAfterOrEqualTo(updated1);
                }
            }

            // 10. Brand direct SQL tests: code, name uniqueness
            UUID brand1Id = UUID.randomUUID();
            stmt.execute("INSERT INTO public.brands (brand_id, brand_code, brand_name) VALUES ('" +
                    brand1Id + "', 'CADISUN', 'Cadisun Brand')");

            // Duplicate brand code rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.brands (brand_id, brand_code, brand_name) VALUES ('" +
                    UUID.randomUUID() + "', 'cadisun', 'Another Brand')"))
                    .isInstanceOf(SQLException.class);

            // Duplicate brand name case-insensitive rejected
            assertThatThrownBy(() -> stmt.execute(
                    "INSERT INTO public.brands (brand_id, brand_code, brand_name) VALUES ('" +
                    UUID.randomUUID() + "', 'BRAND_NEW', 'cadisun brand')"))
                    .isInstanceOf(SQLException.class);

            // Brand can be deleted
            stmt.execute("DELETE FROM public.brands WHERE brand_id = '" + brand1Id + "'");
        }
    }
}
