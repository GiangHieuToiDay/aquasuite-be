package com.aquasuite.ims.module.catalog.internal.migration;

import static org.assertj.core.api.Assertions.*;

import com.aquasuite.ims.support.BundleCoreTestSupport;

import org.junit.jupiter.api.Test;

import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
            try (var c = open();
                    var st = c.createStatement()) {
                st.execute(
                        Files.readString(Path.of("src/test/resources/db/uc13-core-baseline.sql")));
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

    private int runner(Path directory) throws Exception {
        var command =
                new ArrayList<>(
                        List.of(
                                "powershell.exe",
                                "-NoProfile",
                                "-ExecutionPolicy",
                                "Bypass",
                                "-File",
                                Path.of("scripts/db/Apply-Uc13Phase1.ps1")
                                        .toAbsolutePath()
                                        .toString(),
                                "-ContainerId",
                                BundleCoreTestSupport.DB.getContainerId(),
                                "-Database",
                                database,
                                "-Username",
                                BundleCoreTestSupport.DB.getUsername()));
        if (directory != null) {
            command.add("-SqlDirectory");
            command.add(directory.toAbsolutePath().toString());
        }
        var process = new ProcessBuilder(command).redirectErrorStream(true).start();
        var output =
                new String(
                        process.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8);
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        System.out.println(output);
        return process.exitValue();
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
    void realRunnerBackfillRetryAndChecksumMismatch() throws Exception {
        isolated(
                () -> {
                    assertThat(runner(null)).isZero();
                    assertThat(runner(null)).isZero();
                    assertThat(
                                    count(
                                            "select count(*) from bundle_configurations where package_price is null and publication_status='DRAFT'"))
                            .isEqualTo(2);
                    assertThat(count("select count(*) from products where status='DELETED'"))
                            .isEqualTo(1);
                    assertThat(count("select count(*) from bundle_items where quantity=2"))
                            .isEqualTo(1);
                    assertThat(count("select count(*) from catalog_bundle_schema_versions"))
                            .isEqualTo(1);
                    Path temp = Files.createTempDirectory("uc13-checksum-");
                    try {
                        for (String f :
                                List.of(
                                        "uc13-phase1-preflight.sql",
                                        "uc13-phase1-v001.sql",
                                        "uc13-phase1-verify.sql"))
                            Files.copy(
                                    Path.of("src/main/resources/db/manual/" + f), temp.resolve(f));
                        Files.writeString(
                                temp.resolve("uc13-phase1-v001.sql"),
                                "\n-- different bytes",
                                StandardOpenOption.APPEND);
                        assertThat(runner(temp)).isNotZero();
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
                    assertThat(runner(null)).isNotZero();
                    assertThat(
                                    count(
                                            "select count(*) from information_schema.tables where table_schema='public' and table_name in ('bundle_configurations','catalog_bundle_schema_versions','audit_entries')"))
                            .isZero();
                });
    }

    @Test
    void databaseConstraintsRejectInvalidAndPreserveBaseTables() throws Exception {
        isolated(
                () -> {
                    assertThat(runner(null)).isZero();
                    try (var c = open();
                            var st = c.createStatement()) {
                        assertThatThrownBy(() -> st.execute("update bundle_items set quantity=0"))
                                .isInstanceOf(SQLException.class);
                        assertThatThrownBy(
                                        () ->
                                                st.execute(
                                                        "insert into bundle_items select gen_random_uuid(),bundle_product_id,variant_id,quantity,created_at from bundle_items"))
                                .isInstanceOf(SQLException.class);
                        assertThatThrownBy(
                                        () ->
                                                st.execute(
                                                        "update bundle_configurations set publication_status='PUBLISHED'"))
                                .isInstanceOf(SQLException.class);
                    }
                    assertThat(
                                    count(
                                            "select count(*) from information_schema.tables where table_schema='public' and table_name in ('media_assets','inventories')"))
                            .isZero();
                });
    }
}
