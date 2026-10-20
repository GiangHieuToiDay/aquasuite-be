package com.aquasuite.ims.support;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import java.sql.DriverManager;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

/** Verifies migration lifecycle against disposable PostgreSQL databases only. */
class FlywayMigrationIT {
    private String database() throws Exception {
        String name = "flyway_" + UUID.randomUUID().toString().replace("-", "");
        var db = BundleCoreTestSupport.DB;
        try (var c = DriverManager.getConnection(db.getJdbcUrl(), db.getUsername(), db.getPassword());
             var s = c.createStatement()) { s.execute("CREATE DATABASE " + name); }
        return db.getJdbcUrl().substring(0, db.getJdbcUrl().lastIndexOf('/') + 1) + name;
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration config(String url) {
        var db = BundleCoreTestSupport.DB;
        return Flyway.configure().dataSource(url, db.getUsername(), db.getPassword())
                .cleanDisabled(true).baselineOnMigrate(false);
    }

    @Test void emptyDatabaseMigratesAndRetryDoesNothing() throws Exception {
        var flyway = config(database()).load();
        assertEquals(7, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(FlywayException.class, flyway::clean);
    }

    @Test void nonemptyDatabaseRequiresExplicitBaseline() throws Exception {
        String url = database();
        try (var c = DriverManager.getConnection(url, BundleCoreTestSupport.DB.getUsername(),
                BundleCoreTestSupport.DB.getPassword()); var s = c.createStatement()) {
            s.execute("CREATE TABLE unmanaged_data(id integer primary key)");
            s.execute("INSERT INTO unmanaged_data VALUES (42)");
        }
        assertThrows(FlywayException.class, () -> config(url).load().migrate());
    }

    @Test void legacyBaselineBackfillsAndCurrentBaselinePreservesData() throws Exception {
        String url = database();
        // Represents an existing pre-combo database: V1 schema without Flyway history.
        var legacy = config(url).target("1").load();
        legacy.migrate();
        var db = BundleCoreTestSupport.DB;
        try (var c = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
             var s = c.createStatement()) {
            s.execute("DROP TABLE flyway_schema_history");
            s.execute("INSERT INTO products(product_id,category_id,product_code,product_name,product_type,status)"
                    + " VALUES ('10000000-0000-4000-8000-000000000010',"
                    + "'10000000-0000-4000-8000-000000000001','LEGACY','Legacy','BUNDLE','INACTIVE')");
        }
        var upgraded = config(url).target("2").baselineVersion("1").load();
        upgraded.baseline();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        try (var c = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
             var s = c.createStatement()) {
            try (var r = s.executeQuery("SELECT publication_status,version FROM bundle_configurations")) {
                assertTrue(r.next()); assertEquals("DRAFT", r.getString(1)); assertEquals(0, r.getLong(2));
            }
            s.execute("UPDATE bundle_configurations SET package_price=12345,version=7");
            s.execute("DROP TABLE flyway_schema_history");
        }
        var adopted = config(url).baselineVersion("2").load();
        adopted.baseline();
        assertEquals(5, adopted.migrate().migrationsExecuted);
        try (var c = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
             var s = c.createStatement();
             var r = s.executeQuery("SELECT package_price,version FROM bundle_configurations")) {
            assertTrue(r.next()); assertEquals(12345, r.getInt(1)); assertEquals(7, r.getInt(2));
        }
    }
}
