package com.aquasuite.ims.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;

/** Verifies the canonical baseline against disposable PostgreSQL databases only. */
class FlywayMigrationIT {
    private String database() throws Exception {
        String name = "flyway_" + UUID.randomUUID().toString().replace("-", "");
        var db = BundleCoreTestSupport.DB;
        try (var c =
                        DriverManager.getConnection(
                                db.getJdbcUrl(), db.getUsername(), db.getPassword());
                var s = c.createStatement()) {
            s.execute("CREATE DATABASE " + name);
        }
        return db.getJdbcUrl().substring(0, db.getJdbcUrl().lastIndexOf('/') + 1) + name;
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration config(String url) {
        var db = BundleCoreTestSupport.DB;
        return Flyway.configure()
                .dataSource(url, db.getUsername(), db.getPassword())
                .cleanDisabled(true)
                .baselineOnMigrate(false);
    }

    @Test
    void emptyDatabaseMigratesAndRetryDoesNothing() throws Exception {
        var flyway = config(database()).load();
        assertEquals(3, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(FlywayException.class, flyway::clean);
    }

    @Test
    void nonemptyDatabaseRequiresExplicitBaseline() throws Exception {
        String url = database();
        try (var c =
                        DriverManager.getConnection(
                                url,
                                BundleCoreTestSupport.DB.getUsername(),
                                BundleCoreTestSupport.DB.getPassword());
                var s = c.createStatement()) {
            s.execute("CREATE TABLE unmanaged_data(id integer primary key)");
            s.execute("INSERT INTO unmanaged_data VALUES (42)");
        }
        assertThrows(FlywayException.class, () -> config(url).load().migrate());
    }

    @Test
    void categoryInsertUsesDatabaseTimestampDefaults() throws Exception {
        String url = database();
        config(url).load().migrate();

        var db = BundleCoreTestSupport.DB;
        try (var c = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
                var s = c.createStatement()) {
            s.execute(
                    "INSERT INTO public.categories "
                            + "(category_id, category_code, category_name, status) VALUES "
                            + "('10000000-0000-4000-8000-000000000099', "
                            + "'TIMESTAMP_DEFAULT_TEST', 'Timestamp default test', 'ACTIVE')");
            try (var r =
                    s.executeQuery(
                            "SELECT created_at, updated_at FROM public.categories "
                                    + "WHERE category_code = 'TIMESTAMP_DEFAULT_TEST'")) {
                assertTrue(r.next());
                assertNotNull(r.getTimestamp("created_at"));
                assertNotNull(r.getTimestamp("updated_at"));
            }
        }
    }
}
