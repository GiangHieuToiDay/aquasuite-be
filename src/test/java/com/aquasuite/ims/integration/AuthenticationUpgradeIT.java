package com.aquasuite.ims.integration;

import com.aquasuite.ims.support.BundleCoreTestSupport;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import java.sql.*;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class AuthenticationUpgradeIT {
    String database() throws Exception {
        var db = BundleCoreTestSupport.DB;
        String name = "auth_upgrade_"+UUID.randomUUID().toString().replace("-", "");
        try (var c = DriverManager.getConnection(db.getJdbcUrl(),db.getUsername(),db.getPassword()); var s = c.createStatement()) {
            s.execute("CREATE DATABASE "+name);
        }
        return db.getJdbcUrl().substring(0,db.getJdbcUrl().lastIndexOf('/')+1)+name;
    }
    org.flywaydb.core.api.configuration.FluentConfiguration flyway(String url) {
        return Flyway.configure().dataSource(url,BundleCoreTestSupport.DB.getUsername(),BundleCoreTestSupport.DB.getPassword());
    }
    Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url,BundleCoreTestSupport.DB.getUsername(),BundleCoreTestSupport.DB.getPassword());
    }

    @Test void upgradePreservesOldAccountRecoveryAuditAndComboRevision() throws Exception {
        String url=database(); flyway(url).target("3").load().migrate();
        UUID aggregate=UUID.randomUUID(), category=UUID.randomUUID();
        try(var c=connect(url);var s=c.createStatement()) {
            s.execute("INSERT INTO users(id,username,email,password,avatar,status,email_sent_count,verification_code,verification_expired_at) VALUES(7,'legacy-user','legacy@test.invalid','{disabled-test-hash}','https://test.invalid/avatar','ACTIVE',3,'{disabled-otp-hash}',CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO audit_entries(id,actor_id,action,aggregate_type,aggregate_id,occurred_at) VALUES('"+UUID.randomUUID()+"',7,'TEST','BUNDLE','"+aggregate+"',CURRENT_TIMESTAMP)");
            s.execute("INSERT INTO categories(category_id,category_code,category_name,status) VALUES('"+category+"','CAT','Category','ACTIVE')");
            s.execute("INSERT INTO products(product_id,category_id,product_code,product_name,product_type,status) VALUES('"+aggregate+"','"+category+"','COMBO','Preserved','BUNDLE','ACTIVE')");
            s.execute("INSERT INTO bundle_configurations(bundle_product_id,package_price,version) VALUES('"+aggregate+"',95,7)");
            s.execute("INSERT INTO stock_movements(movement_id,inventory_id,variant_id,movement_type,quantity,balance_after,reference_type,created_at,created_by) VALUES('"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','"+UUID.randomUUID()+"','ADJUSTMENT_IN',1,1,'MANUAL',CURRENT_TIMESTAMP,7)");
            s.execute("INSERT INTO stock_counts(stock_count_id,stock_count_code,warehouse_code,status,created_at,updated_at,counted_by,approved_by) VALUES('"+UUID.randomUUID()+"','COUNT','WH_MAIN','DRAFT',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,7,42)");
        }
        assertThat(flyway(url).load().migrate().migrationsExecuted).isEqualTo(4);
        assertThat(flyway(url).load().migrate().migrationsExecuted).isZero();
        try(var c=connect(url);var s=c.createStatement()) {
            UUID mapped;
            try(var r=s.executeQuery("SELECT user_id FROM iam_legacy_user_ids WHERE legacy_id=7")) {
                assertThat(r.next()).isTrue(); mapped=r.getObject(1,UUID.class);
            }
            try(var r=s.executeQuery("SELECT id,password_hash,avatar_url FROM users WHERE username='legacy-user'")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(mapped);
                assertThat(r.getString(2)).isEqualTo("{disabled-test-hash}");assertThat(r.getString(3)).isEqualTo("https://test.invalid/avatar");
            }
            try(var r=s.executeQuery("SELECT actor_id FROM audit_entries")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(mapped);
            }
            try(var r=s.executeQuery("SELECT user_id,verification_code,email_sent_count FROM user_password_recovery")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(mapped);
                assertThat(r.getString(2)).isEqualTo("{disabled-otp-hash}");assertThat(r.getInt(3)).isEqualTo(3);
            }
            try(var r=s.executeQuery("SELECT id,password FROM iam_legacy_users")) {
                assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(7);assertThat(r.getString(2)).isEqualTo("{disabled-test-hash}");
            }
            try(var r=s.executeQuery("SELECT counted_by,approved_by FROM stock_counts")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(mapped);assertThat(r.getObject(2,UUID.class)).isNotNull();
            }
            try(var r=s.executeQuery("SELECT created_by FROM stock_movements")) {
                assertThat(r.next()).isTrue();assertThat(r.getObject(1,UUID.class)).isEqualTo(mapped);
            }
            try(var r=s.executeQuery("SELECT package_price,version FROM bundle_configurations")) {
                assertThat(r.next()).isTrue();assertThat(r.getBigDecimal(1)).isEqualByComparingTo("95");assertThat(r.getLong(2)).isEqualTo(7);
            }
        }
    }

    @Test void conflictingLegacyIdentitiesRollBackTheWholeUuidMigration() throws Exception {
        String url=database();flyway(url).target("3").load().migrate();
        try(var c=connect(url);var s=c.createStatement()) {
            s.execute("INSERT INTO users(id,username,email,password,status) VALUES(1,'Owner','one@test.invalid','{disabled}','ACTIVE'),(2,'owner','two@test.invalid','{disabled}','ACTIVE')");
        }
        assertThatThrownBy(() -> flyway(url).load().migrate()).isInstanceOf(org.flywaydb.core.api.FlywayException.class);
        try(var c=connect(url);var s=c.createStatement();var r=s.executeQuery("SELECT count(*) FROM users WHERE id IN (1,2)")) {
            assertThat(r.next()).isTrue();assertThat(r.getInt(1)).isEqualTo(2);
        }
    }
    @Test void incompleteLegacyAccountIsPreservedAndLocked() throws Exception {
        String url=database();flyway(url).target("3").load().migrate();
        try(var c=connect(url);var s=c.createStatement()) {
            s.execute("INSERT INTO users(id,status) VALUES(13,'ACTIVE')");
        }
        flyway(url).load().migrate();
        try(var c=connect(url);var s=c.createStatement();var r=s.executeQuery(
                "SELECT u.status,u.email,l.email AS old_email FROM users u JOIN iam_legacy_user_ids m ON m.user_id=u.id JOIN iam_legacy_users l ON l.id=m.legacy_id WHERE l.id=13")) {
            assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("LOCKED");
            assertThat(r.getString(2)).endsWith("@users.invalid");assertThat(r.getString(3)).isNull();
        }
    }

}
