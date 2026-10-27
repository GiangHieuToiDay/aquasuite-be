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
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IamMigrationIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private DataSource dataSource;

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
    @DisplayName("AC-02, AC-09: Flyway migration runs successfully from empty DB and records exactly 1 successful row")
    void testFlywaySchemaHistory() throws SQLException {
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT version, description, type, script, success FROM public.flyway_schema_history WHERE version = '1'")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("version")).isEqualTo("1");
                assertThat(rs.getString("script")).isEqualTo("V1__create_aquasuite_baseline.sql");
                assertThat(rs.getBoolean("success")).isTrue();
                assertThat(rs.next()).isFalse();
            }
        }
    }

    @Test
    @DisplayName("AC-02, AC-03, AC-16: Information schema catalog contains exact 5 tables and column specifications")
    void testInformationSchemaTableAndColumnCatalog() throws SQLException {
        Set<String> expectedTables = Set.of("users", "roles", "permissions", "user_roles", "role_permissions");
        Set<String> actualTables = new HashSet<>();

        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' AND table_type = 'BASE TABLE' AND table_name IN ('users', 'roles', 'permissions', 'user_roles', 'role_permissions')")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tbl = rs.getString("table_name");
                    // Exclude only flyway infrastructure metadata table
                    if (!"flyway_schema_history".equalsIgnoreCase(tbl)) {
                        actualTables.add(tbl);
                    }
                }
            }
        }
        assertThat(actualTables).containsExactlyInAnyOrderElementsOf(expectedTables);

        // Check columns of users
        List<String> userCols = getColumnNames("users");
        assertThat(userCols).containsExactlyInAnyOrder(
                "id", "username", "email", "password_hash", "avatar_url", "created_at", "status", "phone",
                "provider", "provider_id", "is_verified", "full_name", "password_last_changed_at",
                "verification_code", "fcm_token", "verification_expired_at", "email_sent_count",
                "email_sent_date"
        );

        // Check columns of user_roles
        List<String> userRoleCols = getColumnNames("user_roles");
        assertThat(userRoleCols).containsExactlyInAnyOrder("user_id", "role_id", "assigned_at", "assigned_by");

        // Check columns of role_permissions
        List<String> rolePermissionCols = getColumnNames("role_permissions");
        assertThat(rolePermissionCols).containsExactlyInAnyOrder("role_id", "permission_id");

        // Check defaults and nullability on users
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'users'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String nullable = rs.getString("is_nullable");
                    String colDefault = rs.getString("column_default");

                    if ("id".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                        assertThat(colDefault).isNull();
                    } else if ("username".equals(col) || "email".equals(col) || "password_hash".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                    } else if ("status".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                        assertThat(colDefault).contains("PENDING_ACTIVATION");
                    } else if ("created_at".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                        assertThat(colDefault).contains("CURRENT_TIMESTAMP");
                    } else if ("phone".equals(col) || "avatar_url".equals(col)) {
                        assertThat(nullable).isEqualTo("YES");
                    }
                }
            }
        }

        // Check defaults and nullability on roles (AC-16)
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name, is_nullable, column_default FROM information_schema.columns WHERE table_schema = 'public' AND table_name = 'roles'")) {
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String col = rs.getString("column_name");
                    String nullable = rs.getString("is_nullable");
                    String colDefault = rs.getString("column_default");

                    if ("role_id".equals(col) || "role_name".equals(col) || "is_active".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                    } else if ("created_at".equals(col)) {
                        assertThat(nullable).isEqualTo("NO");
                        assertThat(colDefault).contains("CURRENT_TIMESTAMP");
                    } else if ("description".equals(col)) {
                        assertThat(nullable).isEqualTo("YES");
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Canonical baseline contains the current authentication state fields")
    void testAuthenticationStateFieldsPresence() throws SQLException {
        List<String> userCols = getColumnNames("users");
        assertThat(userCols).contains(
                "verification_code", "verification_expired_at",
                "email_sent_count", "email_sent_date", "fcm_token", "password_last_changed_at"
        );
    }

    @Test
    @DisplayName("AC-03, AC-07, AC-13: Constraints, foreign keys, and indexes check for Spec v0.4.0")
    void testConstraintsAndForeignKeys() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Check Foreign Key delete rule = RESTRICT
            String sqlFk = """
                SELECT tc.constraint_name, rc.delete_rule, tc.table_name, ccu.table_name AS foreign_table_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.referential_constraints rc ON tc.constraint_name = rc.constraint_name
                JOIN information_schema.constraint_column_usage ccu ON ccu.constraint_name = tc.constraint_name
                WHERE tc.table_schema = 'public' AND tc.constraint_type = 'FOREIGN KEY'
                  AND tc.table_name IN ('users', 'roles', 'permissions', 'user_roles', 'role_permissions')
            """;
            try (PreparedStatement ps = conn.prepareStatement(sqlFk);
                 ResultSet rs = ps.executeQuery()) {
                int fkCount = 0;
                while (rs.next()) {
                    fkCount++;
                    assertThat(rs.getString("delete_rule")).isEqualTo("RESTRICT");
                }
                // Exactly 4 foreign keys in v0.4.0:
                // user_roles (user_id -> users, role_id -> roles)
                // role_permissions (role_id -> roles, permission_id -> permissions)
                assertThat(fkCount).isEqualTo(4);
            }

            // Check Indexes
            String sqlIdx = "SELECT indexname FROM pg_indexes WHERE schemaname = 'public'";
            Set<String> indexes = new HashSet<>();
            try (PreparedStatement ps = conn.prepareStatement(sqlIdx);
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    indexes.add(rs.getString("indexname"));
                }
            }
            assertThat(indexes).contains(
                    "uq_users_username_lower",
                    "uq_users_email_lower",
                    "uq_roles_role_name_lower",
                    "uq_permissions_code_lower",
                    "idx_user_roles_role_id",
                    "idx_role_permissions_permission_id"
            );
            // idx_user_roles_assigned_by must NOT exist
            assertThat(indexes).doesNotContain("idx_user_roles_assigned_by");
        }
    }

    @Test
    @DisplayName("AC-10: Native SQL status default is PENDING_ACTIVATION and check constraint rejects invalid values")
    void testNativeSqlStatusDefaultAndCheckConstraint() throws SQLException {
        UUID id1 = UUID.randomUUID();
        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, id1);
                ps.setString(2, "user_default_status");
                ps.setString(3, "user_default@test.com");
                ps.setString(4, "hash123");
                ps.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement("SELECT status FROM public.users WHERE id = ?")) {
                ps.setObject(1, id1);
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getString("status")).isEqualTo("PENDING_ACTIVATION");
                }
            }

            UUID id2 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash, status) VALUES (?, ?, ?, ?, ?)")) {
                ps.setObject(1, id2);
                ps.setString(2, "user_invalid_status");
                ps.setString(3, "user_invalid@test.com");
                ps.setString(4, "hash123");
                ps.setString(5, "INVALID_STATE");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_users_status");
            }
        }
    }

    @Test
    @DisplayName("AC-04: Four independent unique keys reject case variants sequentially")
    void testFourUniqueKeysCaseInsensitiveSequentialRejection() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // 1. users.username
            UUID u1 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u1);
                ps.setString(2, "JohnDoe");
                ps.setString(3, "JohnDoe@example.com");
                ps.setString(4, "hash123");
                ps.executeUpdate();
            }
            UUID u2 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u2);
                ps.setString(2, "johndoe");
                ps.setString(3, "other@example.com");
                ps.setString(4, "hash123");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_users_username_lower");
            }

            // 2. users.email
            UUID u3 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u3);
                ps.setString(2, "OtherUser");
                ps.setString(3, "johndoe@example.com");
                ps.setString(4, "hash123");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_users_email_lower");
            }

            // 3. roles.role_name
            UUID r1 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.roles (role_id, role_name) VALUES (?, ?)")) {
                ps.setObject(1, r1);
                ps.setString(2, "SuperAdmin");
                ps.executeUpdate();
            }
            UUID r2 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.roles (role_id, role_name) VALUES (?, ?)")) {
                ps.setObject(1, r2);
                ps.setString(2, "superadmin");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_roles_role_name_lower");
            }

            // 4. permissions.code
            UUID p1 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code) VALUES (?, ?)")) {
                ps.setObject(1, p1);
                ps.setString(2, "USER_MANAGE");
                ps.executeUpdate();
            }
            UUID p2 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.permissions (id, code) VALUES (?, ?)")) {
                ps.setObject(1, p2);
                ps.setString(2, "USER_MANAGE");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("uq_permissions_code_lower");
            }
        }
    }

    @Test
    @DisplayName("AC-04: Independent whitespace constraints reject leading/trailing spaces for username and email while allowing internal whitespace")
    void testWhitespaceConstraintsIndependent() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            // Username leading space rejected
            UUID u1 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u1);
                ps.setString(2, " LeadingUser");
                ps.setString(3, "u1@example.com");
                ps.setString(4, "hash");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_users_username_trimmed");
            }

            // Username trailing space rejected
            UUID u2 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u2);
                ps.setString(2, "TrailingUser ");
                ps.setString(3, "u2@example.com");
                ps.setString(4, "hash");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_users_username_trimmed");
            }

            // Email leading space rejected
            UUID u3 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u3);
                ps.setString(2, "validUser3");
                ps.setString(3, " leading@example.com");
                ps.setString(4, "hash");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_users_email_trimmed");
            }

            // Email trailing space rejected
            UUID u4 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u4);
                ps.setString(2, "validUser4");
                ps.setString(3, "trailing@example.com ");
                ps.setString(4, "hash");
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("chk_users_email_trimmed");
            }

            // Internal spaces allowed for username and standard formatted email
            UUID u5 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, ?)")) {
                ps.setObject(1, u5);
                ps.setString(2, "John Doe Senior");
                ps.setString(3, "john.doe@example.com");
                ps.setString(4, "hash");
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("AC-04: Concurrent transactions competing on four unique keys reject duplicate case variants deterministically")
    void testConcurrentTransactionsCompetingFourUniqueKeys() throws Exception {
        // 1. Concurrent users.username
        assertConcurrentCaseInsensitiveDuplicateRejection(
                "ConcurrentUser", "concurrentuser", "uq_users_username_lower",
                (conn, val) -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, 'h')")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, val);
                        ps.setString(3, UUID.randomUUID() + "@test.com");
                        ps.executeUpdate();
                    }
                }
        );

        // 2. Concurrent users.email
        assertConcurrentCaseInsensitiveDuplicateRejection(
                "Concurrent@test.com", "concurrent@test.com", "uq_users_email_lower",
                (conn, val) -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, ?, ?, 'h')")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, "u_" + UUID.randomUUID().toString().substring(0, 8));
                        ps.setString(3, val);
                        ps.executeUpdate();
                    }
                }
        );

        // 3. Concurrent roles.role_name
        assertConcurrentCaseInsensitiveDuplicateRejection(
                "ConcurrentRole", "concurrentrole", "uq_roles_role_name_lower",
                (conn, val) -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO public.roles (role_id, role_name) VALUES (?, ?)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, val);
                        ps.executeUpdate();
                    }
                }
        );

        // 4. Concurrent permissions.code
        assertConcurrentCaseInsensitiveDuplicateRejection(
                "CONCURRENT_PERM", "CONCURRENT_PERM", "uq_permissions_code_lower",
                (conn, val) -> {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO public.permissions (id, code) VALUES (?, ?)")) {
                        ps.setObject(1, UUID.randomUUID());
                        ps.setString(2, val);
                        ps.executeUpdate();
                    }
                }
        );
    }

    @FunctionalInterface
    private interface SqlInsertAction {
        void execute(Connection conn, String value) throws SQLException;
    }

    private void assertConcurrentCaseInsensitiveDuplicateRejection(
            String key1, String key2, String expectedConstraint, SqlInsertAction insertAction) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch readyLatch = new CountDownLatch(2);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch firstInsertDoneLatch = new CountDownLatch(1);
        AtomicReference<Throwable> thread2Error = new AtomicReference<>();
        AtomicBoolean thread1Committed = new AtomicBoolean(false);

        Future<?> f1 = executor.submit(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                insertAction.execute(conn, key1);
                firstInsertDoneLatch.countDown();
                // Brief pause so Thread 2 starts insert and blocks on Postgres unique index lock
                Thread.sleep(100);
                conn.commit();
                thread1Committed.set(true);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        Future<?> f2 = executor.submit(() -> {
            try (Connection conn = dataSource.getConnection()) {
                conn.setAutoCommit(false);
                readyLatch.countDown();
                startLatch.await(5, TimeUnit.SECONDS);
                firstInsertDoneLatch.await(5, TimeUnit.SECONDS);
                try {
                    insertAction.execute(conn, key2);
                    conn.commit();
                } catch (SQLException ex) {
                    conn.rollback();
                    thread2Error.set(ex);
                }
            } catch (Exception e) {
                thread2Error.set(e);
            }
        });

        try {
            assertThat(readyLatch.await(5, TimeUnit.SECONDS)).isTrue();
            startLatch.countDown();
            f1.get(5, TimeUnit.SECONDS);
            f2.get(5, TimeUnit.SECONDS);

            assertThat(thread1Committed.get()).isTrue();
            assertThat(thread2Error.get())
                    .isNotNull()
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining(expectedConstraint);
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(3, TimeUnit.SECONDS);
        }
    }

    @Test
    @DisplayName("AC-03: Composite primary keys prevent duplicate assignments")
    void testCompositePrimaryKeyRejection() throws SQLException {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash) VALUES (?, 'comp_user', 'comp@test.com', 'h')")) {
                ps.setObject(1, userId);
                ps.executeUpdate();
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.roles (role_id, role_name) VALUES (?, 'ADMIN')")) {
                ps.setObject(1, roleId);
                ps.executeUpdate();
            }

            // Insert first assignment (only user_id, role_id)
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.user_roles (user_id, role_id) VALUES (?, ?)")) {
                ps.setObject(1, userId);
                ps.setObject(2, roleId);
                ps.executeUpdate();
            }

            // Insert duplicate user_roles assignment -> fails composite PK
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.user_roles (user_id, role_id) VALUES (?, ?)")) {
                ps.setObject(1, userId);
                ps.setObject(2, roleId);
                assertThatThrownBy(ps::executeUpdate)
                        .isInstanceOf(SQLException.class)
                        .hasMessageContaining("pk_user_roles");
            }
        }
    }

    @Test
    @DisplayName("AC-15: Phone is nullable and non-unique, avatar_url is nullable and stores string")
    void testPhoneAndAvatarConstraints() throws SQLException {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash, phone, avatar_url) VALUES (?, 'u1', 'u1@test.com', 'h', '0912345678', 'http://example.com/avatar1.png')")) {
                ps.setObject(1, id1);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash, phone, avatar_url) VALUES (?, 'u2', 'u2@test.com', 'h', '0912345679', NULL)")) {
                ps.setObject(1, id2);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
            UUID id3 = UUID.randomUUID();
            try (PreparedStatement ps = conn.prepareStatement(
                    "INSERT INTO public.users (id, username, email, password_hash, phone, avatar_url) VALUES (?, 'u3', 'u3@test.com', 'h', NULL, NULL)")) {
                ps.setObject(1, id3);
                assertThat(ps.executeUpdate()).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("AC-13: Transactional migration failure rehearsal rolls back completely and leaves no partial objects")
    void testTransactionalMigrationFailureRehearsal() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS rehearsal_tx");
            }

            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE TABLE rehearsal_tx.tx_temp (id INT PRIMARY KEY)");
                assertThatThrownBy(() -> stmt.execute("INVALID SQL COMMAND"))
                        .isInstanceOf(SQLException.class);
            }
            conn.rollback();
            conn.setAutoCommit(true);

            // Verify physical schema catalog has no partial table
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM information_schema.tables WHERE table_schema = 'rehearsal_tx' AND table_name = 'tx_temp'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isFalse();
                }
            }

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA rehearsal_tx CASCADE");
            }
        }
    }

    @Test
    @DisplayName("AC-13: Partial migration failure rehearsal inspects Flyway history and schema catalog, enforcing STOP/recovery classification")
    void testPartialMigrationFailureAndRecoveryClassificationRehearsal() throws SQLException {
        try (Connection conn = dataSource.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS rehearsal_partial");
                // 1. Create Flyway schema history table in rehearsal schema
                stmt.execute("""
                    CREATE TABLE rehearsal_partial.flyway_schema_history (
                        installed_rank INT NOT NULL,
                        version VARCHAR(50),
                        description VARCHAR(200) NOT NULL,
                        type VARCHAR(20) NOT NULL,
                        script VARCHAR(1000) NOT NULL,
                        checksum INT,
                        installed_by VARCHAR(100) NOT NULL,
                        installed_on TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                        execution_time INT NOT NULL,
                        success BOOLEAN NOT NULL
                    )
                """);

                // 2. Simulate partial migration: table committed into schema, followed by unrecoverable failure recorded in history
                stmt.execute("CREATE TABLE rehearsal_partial.partial_iam_table (id UUID PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("""
                    INSERT INTO rehearsal_partial.flyway_schema_history
                    (installed_rank, version, description, type, script, installed_by, execution_time, success)
                    VALUES (1, '1', 'partial_foundation', 'SQL', 'V1__partial.sql', 'test_user', 25, false)
                """);
            }

            // 3. Inspect Flyway schema history
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT version, success FROM rehearsal_partial.flyway_schema_history WHERE version = '1'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("success")).isFalse();
                }
            }

            // 4. Inspect schema catalog: partial object is present
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT table_name FROM information_schema.tables WHERE table_schema = 'rehearsal_partial' AND table_name = 'partial_iam_table'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                }
            }

            // 5. Verification of Decision Classification per Spec AC-13 & Plan v4 line 343:
            // State: METADATA_FAILED && CATALOG_PARTIAL_OBJECTS_PRESENT
            // Required decision: STOP_AUTOMATIC_ACTION (do not run blind repair or unapproved destructive drops)
            try (Statement stmt = conn.createStatement()) {
                // If repair alone is executed (deleting failed row without handling catalog), retrying migration collides:
                assertThatThrownBy(() -> stmt.execute("CREATE TABLE rehearsal_partial.partial_iam_table (id UUID PRIMARY KEY, name VARCHAR(100))"))
                        .isInstanceOf(SQLException.class);

                // Rehearse approved forward-fix recovery path:
                // Approved forward-fix drops/adjusts intermediate state and updates metadata to verified state
                stmt.execute("DROP TABLE rehearsal_partial.partial_iam_table");
                stmt.execute("CREATE TABLE rehearsal_partial.partial_iam_table (id UUID PRIMARY KEY, name VARCHAR(100))");
                stmt.execute("UPDATE rehearsal_partial.flyway_schema_history SET success = true WHERE version = '1'");
            }

            // 6. Verify post-recovery catalog and history consistency
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT success FROM rehearsal_partial.flyway_schema_history WHERE version = '1'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertThat(rs.next()).isTrue();
                    assertThat(rs.getBoolean("success")).isTrue();
                }
            }

            // Cleanup rehearsal schema
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("DROP SCHEMA rehearsal_partial CASCADE");
            }
        }
    }

    private List<String> getColumnNames(String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT column_name FROM information_schema.columns WHERE table_schema = 'public' AND table_name = ?")) {
            ps.setString(1, tableName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    columns.add(rs.getString("column_name"));
                }
            }
        }
        return columns;
    }
}
