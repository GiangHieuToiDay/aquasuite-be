package com.aquasuite.ims.module.iam.internal.registration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import java.sql.DriverManager;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** Exercises upgrades on new disposable databases, never the configured application database. */
class RegisterMigrationIT {
    @Test void v7AddsLinkedProfilesWithoutGuessingProfilesForLegacyUsers() throws Exception {
        String url=freshDatabase(); migrations(url,"6").migrate();
        UUID userId=UUID.randomUUID();
        try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.prepareStatement(
                "INSERT INTO users(id,username,email,password_hash) VALUES (?, 'legacy-profile', 'legacy-profile@example.test', 'synthetic-disabled')")) {
            s.setObject(1,userId); s.executeUpdate();
        }
        assertThat(migrations(url,"7").migrate().migrationsExecuted).isEqualTo(1);
        try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
            try(var rows=s.executeQuery("SELECT count(*) FROM customers")) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
            try(var rows=s.executeQuery("SELECT id FROM users")) { rows.next(); assertThat(rows.getObject(1)).isEqualTo(userId); }
            String invalid="INSERT INTO customers(customer_id,user_id,customer_code,customer_name,phone,email) VALUES ('"
                    +UUID.randomUUID()+"','"+UUID.randomUUID()+"','INVALID','Name','0901234567','invalid@example.test')";
            assertThatThrownBy(() -> s.executeUpdate(invalid)).isInstanceOf(java.sql.SQLException.class).hasMessageContaining("foreign key");
        }
    }
    private static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    static { DB.start(); }

    private String freshDatabase() throws Exception {
        String name="registration_"+UUID.randomUUID().toString().replace("-","");
        try(var c=DriverManager.getConnection(DB.getJdbcUrl(),DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
            s.execute("CREATE DATABASE "+name);
        }
        return "jdbc:postgresql://"+DB.getHost()+":"+DB.getMappedPort(5432)+"/"+name;
    }
    private Flyway migrations(String url,String target) {
        return Flyway.configure().dataSource(url,DB.getUsername(),DB.getPassword())
                .locations("classpath:db/migration").target(target).load();
    }

    @Test void freshAndPopulatedUpgradePreserveUuidAndLegacyNullableFullName() throws Exception {
        String url=freshDatabase(); migrations(url,"5").migrate();
        UUID id=UUID.randomUUID();
        try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.prepareStatement(
                "INSERT INTO users(id,username,email,password_hash,phone,status) VALUES (?, 'legacy', 'legacy@example.test', 'synthetic-disabled', '0901234567','ACTIVE')")) {
            s.setObject(1,id); s.executeUpdate();
        }
        migrations(url,"6").migrate(); migrations(url,"6").validate();
        try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
            try(var rows=s.executeQuery("SELECT id,full_name,status FROM users")) {
                assertThat(rows.next()).isTrue(); assertThat(rows.getObject(1)).isEqualTo(id);
                assertThat(rows.getString(2)).isNull(); assertThat(rows.getString(3)).isEqualTo("ACTIVE");
            }
            assertThatThrownBy(() -> s.executeUpdate("INSERT INTO users(id,username,email,password_hash,phone) VALUES ('"+UUID.randomUUID()+"','other','other@example.test','synthetic-disabled','0901234567')"))
                    .isInstanceOf(java.sql.SQLException.class).hasMessageContaining("uq_users_phone_not_null");
        }
        String empty=freshDatabase(); migrations(empty,"6").migrate(); migrations(empty,"6").validate();
    }

    @Test void duplicateOrNoncanonicalLegacyPhonesBlockUpgradeWithoutDataLoss() throws Exception {
        for(String phone:new String[]{"0901234567","invalid-phone"}) {
            String url=freshDatabase(); migrations(url,"5").migrate();
            try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
                for(int i=0;i<2;i++) s.executeUpdate("INSERT INTO users(id,username,email,password_hash,phone) VALUES ('"+UUID.randomUUID()+"','legacy"+i+"','legacy"+i+"@example.test','synthetic-disabled','"+phone+"')");
            }
            assertThatThrownBy(() -> migrations(url,"6").migrate()).hasMessageContaining("migration");
            try(var c=DriverManager.getConnection(url,DB.getUsername(),DB.getPassword());var s=c.createStatement()) {
                try(var rows=s.executeQuery("SELECT count(*) FROM users")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(2); }
                try(var rows=s.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_name='users' AND column_name='full_name'")) {
                    rows.next(); assertThat(rows.getInt(1)).isZero();
                }
            }
        }
    }
}
