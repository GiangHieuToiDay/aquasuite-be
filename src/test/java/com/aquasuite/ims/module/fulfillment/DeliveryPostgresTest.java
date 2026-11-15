package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import com.aquasuite.ims.module.fulfillment.internal.repository.*;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService;
import com.aquasuite.ims.module.fulfillment.internal.storage.DeliveryPhotoStorage;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests;
import com.aquasuite.ims.module.iam.api.*;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.*;
import java.util.concurrent.*;
import static org.mockito.Mockito.*;
import static org.assertj.core.api.Assertions.*;

/** Isolated PostgreSQL only; never connects to the application's database. */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(DeliveryPostgresTest.Config.class)
class DeliveryPostgresTest {
    @Container static final PostgreSQLContainer<?> DB = new PostgreSQLContainer<>("postgres:16-alpine");
    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = DeliveryRepository.class)
    static class Config {
        @Bean DataSource dataSource() throws Exception {
            var ds = new DriverManagerDataSource(DB.getJdbcUrl(), DB.getUsername(), DB.getPassword());
            try (var c = ds.getConnection()) {
                // Minimal legacy tables isolate this migration from other modules' schema.
                c.createStatement().execute("CREATE TABLE deliveries (delivery_id UUID PRIMARY KEY, delivery_code VARCHAR(100) NOT NULL UNIQUE, order_id UUID NOT NULL, warehouse_code VARCHAR(50) NOT NULL, shipper_id UUID, batch_no INTEGER, delivery_status VARCHAR(32) NOT NULL DEFAULT 'DRAFT', dispatch_date TIMESTAMPTZ, scheduled_date TIMESTAMPTZ, delivered_at TIMESTAMPTZ, recipient_name VARCHAR(255) NOT NULL, recipient_phone VARCHAR(32) NOT NULL, delivery_address TEXT NOT NULL, cod_required BOOLEAN NOT NULL DEFAULT FALSE, cod_amount NUMERIC(15,2) NOT NULL DEFAULT 0, created_by UUID NOT NULL, confirmed_by UUID, confirmed_at TIMESTAMPTZ, note TEXT, created_at TIMESTAMPTZ NOT NULL DEFAULT now(), updated_at TIMESTAMPTZ NOT NULL DEFAULT now())");
                c.createStatement().execute("CREATE TABLE delivery_items (delivery_item_id UUID PRIMARY KEY, delivery_id UUID NOT NULL, order_item_id UUID NOT NULL, inventory_id UUID NOT NULL, variant_id UUID NOT NULL, quantity NUMERIC(12,2) NOT NULL, serial_numbers JSONB)");
                c.createStatement().execute("INSERT INTO deliveries(delivery_id,delivery_code,order_id,warehouse_code,recipient_name,recipient_phone,delivery_address,created_by,delivery_status) VALUES ('00000000-0000-0000-0000-000000000001','legacy','00000000-0000-0000-0000-000000000002','MAIN','Test','000','Test','00000000-0000-0000-0000-000000000003','DELIVERED')");
                ScriptUtils.executeSqlScript(c, new ClassPathResource("db/migration/V31__add_delivery_assignment_and_photos.sql"));
            }
            return ds;
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds); f.setPackagesToScan(Delivery.class.getPackageName());
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "validate")); return f;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory emf) { return new JpaTransactionManager(emf); }
        @Bean IamApi iam() { return mock(IamApi.class); }
        @Bean CatalogApi catalog() { return mock(CatalogApi.class); }
        @Bean DeliveryPhotoStorage storage() { return mock(DeliveryPhotoStorage.class); }
        @Bean DeliveryService service(DeliveryRepository r, DeliveryItemRepository i, IamApi iam, CatalogApi c, DeliveryPhotoStorage s, PlatformTransactionManager m) {
            return new DeliveryService(r,i,iam,c,s,m);
        }
    }
    @Autowired DeliveryRepository repo;
    @Autowired DeliveryService service;
    @Autowired IamApi actor;
    @Autowired IamApi iam;
    @Autowired DataSource ds;
    @Autowired PlatformTransactionManager manager;
    UUID shipper = UUID.randomUUID();
    @BeforeEach void actor() {
        when(actor.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(shipper).roles(List.of("SHIPPER")).build());
    }
    Delivery insert(String status) {
        return repo.saveAndFlush(Delivery.builder().deliveryId(UUID.randomUUID()).deliveryCode(UUID.randomUUID().toString())
            .orderId(UUID.randomUUID()).warehouseCode("MAIN").recipientName("Test").recipientPhone("000")
            .deliveryAddress("Test").createdBy(shipper).shipperId(shipper).deliveryStatus(status)
            .confirmedAt(java.time.OffsetDateTime.now()).build());
    }
    @Test void migrationPreservesLegacyWithoutInventingProof() {
        var jdbc = new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("select delivery_status from deliveries where delivery_code='legacy'", String.class)).isEqualTo("DELIVERED");
        assertThat(jdbc.queryForObject("select proof_photos::text from deliveries where delivery_code='legacy'", String.class)).isEqualTo("[]");
        assertThat(jdbc.queryForObject("select count(*) from deliveries where delivery_code='legacy' and assigned_at is null and started_at is null", Integer.class)).isEqualTo(1);
    }
    @Test void jsonPhotoMetadataRoundTrips() {
        var d = insert("IN_TRANSIT");
        var photoId = UUID.randomUUID();
        d.setProofPhotos(List.of(new Delivery.Photo(photoId, "key", "image/png", 12)));
        repo.saveAndFlush(d);
        assertThat(repo.findById(d.getDeliveryId()).orElseThrow().getProofPhotos().getFirst().photoId()).isEqualTo(photoId);
    }
    @Test void concurrentStartPreservesExactlyOneTimestamp() throws Exception {
        var d = insert("ASSIGNED");
        var gate = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<java.time.OffsetDateTime> start = () -> { gate.await(5, TimeUnit.SECONDS); return service.start(d.getDeliveryId()).startedAt(); };
            var a = pool.submit(start); var b = pool.submit(start);
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void invalidBulkAssignmentRollsBackAllRows() {
        UUID accountant = UUID.randomUUID();
        when(actor.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(accountant).roles(List.of("ACCOUNTANT")).build());
        when(iam.getUserById(shipper)).thenReturn(UserSummaryDto.builder().id(shipper).status("ACTIVE").roles(List.of("SHIPPER")).build());
        var a = insert("DISPATCHED"); var b = insert("DELIVERED");
        assertThatThrownBy(() -> service.assign(new DeliveryRequests.Assign(shipper, List.of(a.getDeliveryId(), b.getDeliveryId())))).isInstanceOf(AppException.class);
        assertThat(repo.findById(a.getDeliveryId()).orElseThrow().getDeliveryStatus()).isEqualTo("DISPATCHED");
    }
    @Test void databaseRejectsNonArrayPhotoMetadata() {
        var d = insert("ASSIGNED");
        assertThatThrownBy(() -> new TransactionTemplate(manager).execute(tx -> {
            new JdbcTemplate(ds).update("update deliveries set proof_photos='{}'::jsonb where delivery_id=?", d.getDeliveryId()); return null;
        })).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
}
