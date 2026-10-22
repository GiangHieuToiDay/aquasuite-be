package com.aquasuite.ims.module.catalog.internal.migration;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;

class BundleCoreMigrationIT {
    private String database;

    private Connection open() throws Exception {
        var pg = BundleCoreTestSupport.DB;
        return DriverManager.getConnection(
                "jdbc:postgresql://" + pg.getHost() + ":" + pg.getMappedPort(5432) + "/" + database,
                pg.getUsername(),
                pg.getPassword());
    }

    private void isolated(Throwing work) throws Exception {
        var pg = BundleCoreTestSupport.DB;
        database = "migration_" + UUID.randomUUID().toString().replace("-", "");
        try (var admin =
                        DriverManager.getConnection(
                                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
                var statement = admin.createStatement()) {
            statement.execute("CREATE DATABASE " + database);
            migration().target("1").load().migrate();
            try (var c = open();
                    var st = c.createStatement()) {
                st.execute(
                        """
                    INSERT INTO products(product_id,category_id,product_code,product_name,product_type,status) VALUES
                    ('10000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000099','NORMAL','Normal','NORMAL','ACTIVE'),
                    ('10000000-0000-4000-8000-000000000002','10000000-0000-4000-8000-000000000099','LEGACY','Legacy','BUNDLE','INACTIVE'),
                    ('10000000-0000-4000-8000-000000000003','10000000-0000-4000-8000-000000000099','DELETED','Deleted','BUNDLE','DELETED');
                    INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit,conversion_rate,weight,cost_price,selling_price,serial_tracking,status)
                    VALUES('20000000-0000-4000-8000-000000000001','10000000-0000-4000-8000-000000000001','SKU','Variant','piece',1,0,0,100,false,'ACTIVE');
                    INSERT INTO bundle_items(bundle_item_id,created_at,bundle_product_id,variant_id,quantity) VALUES('30000000-0000-4000-8000-000000000001',CURRENT_TIMESTAMP,
                    '10000000-0000-4000-8000-000000000002','20000000-0000-4000-8000-000000000001',2);
                    """);
            }
            try {
                work.run();
            } finally {
                statement.execute("DROP DATABASE " + database + " WITH (FORCE)");
            }
        }
    }

    private interface Throwing {
        void run() throws Exception;
    }

    private org.flywaydb.core.api.configuration.FluentConfiguration migration() {
        var pg = BundleCoreTestSupport.DB;
        return Flyway.configure()
                .dataSource(
                        "jdbc:postgresql://"
                                + pg.getHost()
                                + ":"
                                + pg.getMappedPort(5432)
                                + "/"
                                + database,
                        pg.getUsername(),
                        pg.getPassword())
                .cleanDisabled(true);
    }

    private int count(String query) throws Exception {
        try (var c = open();
                var st = c.createStatement();
                var rs = st.executeQuery(query)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    @Test
    void flywayBackfillRetryAndChecksumMismatch() throws Exception {
        isolated(
                () -> {
                    assertThat(migration().target("2").load().migrate().migrationsExecuted)
                            .isEqualTo(1);
                    assertThat(migration().target("2").load().migrate().migrationsExecuted)
                            .isZero();
                    assertThat(
                                    count(
                                            "select count(*) from bundle_configurations where package_price is null and publication_status='DRAFT'"))
                            .isEqualTo(2);
                    assertThat(count("select count(*) from products where status='DELETED'"))
                            .isEqualTo(1);
                    assertThat(count("select count(*) from bundle_items where quantity=2"))
                            .isEqualTo(1);
                    assertThat(count("select count(*) from flyway_schema_history where success"))
                            .isEqualTo(2);
                    Path temp = Files.createTempDirectory("uc13-checksum-");
                    try {
                        for (String f :
                                List.of(
                                        "V1__Initial_legacy_schema.sql",
                                        "V2__SCRUM14_phase1_combo_core.sql"))
                            Files.copy(
                                    Path.of("src/main/resources/db/migration/" + f),
                                    temp.resolve(f));
                        Files.writeString(
                                temp.resolve("V2__SCRUM14_phase1_combo_core.sql"),
                                "\nSELECT 42;",
                                StandardOpenOption.APPEND);
                        assertThatThrownBy(
                                        () ->
                                                migration()
                                                        .target("2")
                                                        .locations(
                                                                "filesystem:"
                                                                        + temp.toAbsolutePath())
                                                        .load()
                                                        .validate())
                                .isInstanceOf(FlywayException.class);
                    } finally {
                        try (var entries = Files.list(temp)) {
                            for (Path file : entries.toList()) Files.delete(file);
                        }
                        Files.delete(temp);
                    }
                });
    }

    @Test
    void invalidLegacyDataRollsBackEntireMigration() throws Exception {
        isolated(
                () -> {
                    try (var c = open();
                            var st = c.createStatement()) {
                        st.execute("update bundle_items set quantity=0");
                    }
                    assertThatThrownBy(() -> migration().target("2").load().migrate())
                            .isInstanceOf(FlywayException.class);
                    assertThat(
                                    count(
                                            "select count(*) from information_schema.tables where table_schema='public' and table_name in ('bundle_configurations','audit_entries')"))
                            .isZero();
                });
    }

    @Test
    void databaseConstraintsRejectInvalidAndPreserveBaseTables() throws Exception {
        isolated(
                () -> {
                    assertThat(migration().target("2").load().migrate().migrationsExecuted)
                            .isEqualTo(1);
                    try (var c = open();
                            var st = c.createStatement()) {
                        assertThatThrownBy(() -> st.execute("update bundle_items set quantity=0"))
                                .isInstanceOf(SQLException.class);
                        assertThatThrownBy(
                                        () ->
                                                st.execute(
                                                        "insert into bundle_items(bundle_item_id,bundle_product_id,variant_id,quantity,created_at) select gen_random_uuid(),bundle_product_id,variant_id,quantity,created_at from bundle_items"))
                                .isInstanceOf(SQLException.class);
                        assertThatThrownBy(
                                        () ->
                                                st.execute(
                                                        "update bundle_configurations set publication_status='PUBLISHED'"))
                                .isInstanceOf(SQLException.class);
                    }
                    assertThat(
                                    count(
                                            "select count(*) from information_schema.tables where table_schema='public' and table_name='inventories'"))
                            .isEqualTo(1);
                });
    }
}
