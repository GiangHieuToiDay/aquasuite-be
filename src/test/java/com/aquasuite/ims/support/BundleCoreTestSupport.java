package com.aquasuite.ims.support;

import com.aquasuite.ims.module.audit.internal.service.AuditService;
import com.aquasuite.ims.module.catalog.internal.config.BundleClockConfig;
import com.aquasuite.ims.module.catalog.internal.controller.*;
import com.aquasuite.ims.module.catalog.internal.mapper.BundleCoreMapper;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.*;
import com.aquasuite.ims.share.exception.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.*;
import java.util.*;

@SpringBootTest(classes = BundleCoreTestSupport.Config.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class BundleCoreTestSupport {
    public static final UUID CATEGORY = UUID.fromString("10000000-0000-4000-8000-000000000001");
    public static final UUID NORMAL = UUID.fromString("10000000-0000-4000-8000-000000000002");
    public static final UUID SKU = UUID.fromString("20000000-0000-4000-8000-000000000001");
    public static final PostgreSQLContainer<?> DB;

    static {
        java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone("UTC"));
        DB = new PostgreSQLContainer<>("postgres:17.6").withPassword(UUID.randomUUID().toString());
        DB.start();
        Runtime.getRuntime().addShutdownHook(new Thread(DB::stop));
    }

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", DB::getJdbcUrl);
        r.add("spring.datasource.username", DB::getUsername);
        r.add("spring.datasource.password", DB::getPassword);
        r.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
        r.add("spring.jpa.open-in-view", () -> "false");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @EntityScan(
            basePackages = {
                "com.aquasuite.ims.module.catalog.internal.entity",
                "com.aquasuite.ims.module.iam.internal.entity",
                "com.aquasuite.ims.module.audit.internal.entity"
            })
    @EnableJpaRepositories(
            basePackages = {
                "com.aquasuite.ims.module.catalog.internal.repository",
                "com.aquasuite.ims.module.iam.internal.repository",
                "com.aquasuite.ims.module.audit.internal.repository"
            })
    @Import({
        BundleAdminService.class,
        BundlePricingService.class,
        BundleCoreMapper.class,
        BundleSkuQueryService.class,
        BundleClockConfig.class,
        AuditService.class,
        BundleAdminController.class,
        BundleSkuController.class,
        GlobalExceptionHandler.class
    })
    public static class Config {
        @org.springframework.context.annotation.Bean
        org.springframework.transaction.support.TransactionTemplate transactionTemplate(
                org.springframework.transaction.PlatformTransactionManager manager) {
            return new org.springframework.transaction.support.TransactionTemplate(manager);
        }

        @org.springframework.context.annotation.Bean
        LegacyProbe legacyProbe() {
            return new LegacyProbe();
        }
    }

    @org.springframework.web.bind.annotation.RestController
    public static class LegacyProbe {
        @org.springframework.web.bind.annotation.GetMapping("/test-legacy-endpoint")
        public String legacy() {
            return "legacy";
        }
    }

    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected BundleAdminService service;
    @Autowired protected ObjectMapper json;
    @Autowired protected MockMvc mvc;
    @Autowired protected TransactionTemplate tx;

    @BeforeEach
    public void fixture() {
        jdbc.execute(
                "TRUNCATE"
                    + " audit_entries,bundle_items,bundle_configurations,product_images,product_variants,products,categories,brands,users"
                    + " RESTART IDENTITY CASCADE");
        jdbc.update(
                "INSERT INTO users(id,username,status)"
                    + " VALUES(1,'owner','ACTIVE'),(2,'inactive','INACTIVE')");
        jdbc.update(
                "INSERT INTO categories(category_id,category_code,category_name,status)"
                    + " VALUES(?,?,?,'ACTIVE')",
                CATEGORY,
                "CAT",
                "Category");
        jdbc.update(
                "INSERT INTO"
                    + " products(product_id,product_code,category_id,product_name,product_type,status,created_at,updated_at)"
                    + " VALUES(?,?,?,?,'NORMAL','ACTIVE',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                NORMAL,
                "NORMAL",
                CATEGORY,
                "Normal product");
        jdbc.update(
                "INSERT INTO"
                    + " product_variants(variant_id,product_id,sku,variant_name,unit,conversion_rate,weight,cost_price,selling_price,serial_tracking,status)"
                    + " VALUES(?,?,?,?,?,1,0,0,110000000,false,'ACTIVE')",
                SKU,
                NORMAL,
                "SKU-1",
                "Variant",
                "piece");
    }

    protected BundleCoreCreateRequest request(String code) {
        return new BundleCoreCreateRequest(
                code,
                "Combo",
                CATEGORY,
                null,
                null,
                List.of(new BundleCoreCreateRequest.Item(SKU, 1)),
                new java.math.BigDecimal("95000000.00"));
    }

    protected BundleCoreDetail create() {
        return service.create(request("BUNDLE-1"));
    }

    protected BundleCoreUpdateRequest update(BundleCoreDetail d) {
        return new BundleCoreUpdateRequest(
                d.productCode(),
                "Updated combo",
                CATEGORY,
                null,
                null,
                List.of(new BundleCoreCreateRequest.Item(SKU, 2)),
                new java.math.BigDecimal("95000000.00"),
                d.version());
    }
}
