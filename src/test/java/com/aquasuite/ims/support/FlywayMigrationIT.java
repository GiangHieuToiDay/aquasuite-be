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
        assertEquals(26, flyway.migrate().migrationsExecuted);
        assertTrue(flyway.validateWithResult().validationSuccessful);
        assertEquals(0, flyway.migrate().migrationsExecuted);
        assertThrows(FlywayException.class, flyway::clean);
    }

    @Test
    void v23PreservesSkuQuotesAndSupportsPackageRootsWithRealVariantChildren() throws Exception {
        String url=database();
        config(url).target("22").load().migrate();
        var db=BundleCoreTestSupport.DB;
        UUID user=UUID.randomUUID(),customer=UUID.randomUUID(),category=UUID.randomUUID();
        UUID product=UUID.randomUUID(),variant=UUID.randomUUID(),quote=UUID.randomUUID();
        UUID normal=UUID.randomUUID(),bundle=UUID.randomUUID(),root=UUID.randomUUID();
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            sql.execute("insert into users(id,username,email,password_hash,status) values('"+user+"','v23-user','v23@example.invalid','{disabled-test-account}','ACTIVE')");
            sql.execute("insert into customers(customer_id,customer_code,customer_name,phone) values('"+customer+"','V23-C','Migration','0900000000')");
            sql.execute("insert into categories(category_id,category_code,category_name) values('"+category+"','V23_CAT','Migration')");
            sql.execute("insert into products(product_id,product_code,product_name,category_id) values('"+product+"','V23-P','SKU product','"+category+"')");
            sql.execute("insert into products(product_id,product_code,product_name,category_id,product_type) values('"+bundle+"','V23-B','Bundle','"+category+"','BUNDLE')");
            sql.execute("insert into product_variants(variant_id,product_id,sku,variant_name,unit) values('"+variant+"','"+product+"','V23-SKU','SKU','piece')");
            sql.execute("insert into quotations(quotation_id,quotation_code,customer_id,project_name,quotation_date,valid_until,created_by) values('"+quote+"','V23-Q','"+customer+"','Migration',current_date,current_date+1,'"+user+"')");
            sql.execute("insert into quotation_items(quotation_item_id,quotation_id,variant_id,quantity,unit_price,sku,unit) values('"+normal+"','"+quote+"','"+variant+"',2,100,'V23-SKU','piece')");
        }
        var upgraded=config(url).target("23").load();
        assertEquals(1,upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        assertEquals(0,upgraded.migrate().migrationsExecuted);
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            try (var row=sql.executeQuery("select variant_id,quantity,unit_price from quotation_items where quotation_item_id='"+normal+"'")) {
                assertTrue(row.next()); assertEquals(variant,row.getObject(1,UUID.class));
                assertEquals(2,row.getBigDecimal(2).intValue()); assertEquals(100,row.getBigDecimal(3).intValue());
            }
            sql.execute("insert into quotation_items(quotation_item_id,quotation_id,combo_id,combo_name,item_type,quantity,unit_price,sku,unit) values('"+root+"','"+quote+"','"+bundle+"','Snapshot','COMBO_HEADER',1,150,'V23-B','set')");
            sql.execute("insert into quotation_items(quotation_item_id,quotation_id,variant_id,parent_item_id,item_type,quantity,unit_price,sku,unit) values('"+UUID.randomUUID()+"','"+quote+"','"+variant+"','"+root+"','COMBO_COMPONENT',1,200,'V23-SKU','piece')");
            assertThrows(java.sql.SQLException.class,() -> sql.execute("insert into quotation_items(quotation_item_id,quotation_id,variant_id,quantity,unit_price,sku,unit) values('"+UUID.randomUUID()+"','"+quote+"','"+UUID.randomUUID()+"',1,100,'bad','piece')"));
            assertThrows(java.sql.SQLException.class,() -> sql.execute("insert into quotation_items(quotation_item_id,quotation_id,quantity,unit_price,sku,unit) values('"+UUID.randomUUID()+"','"+quote+"',1,100,'bad','piece')"));
            sql.execute("delete from quotation_items where quotation_id='"+quote+"'");
        }
    }

    @Test
    void v24AllowsRevisionsButStillRejectsDuplicateCodeAndRevision() throws Exception {
        String url=database();
        config(url).target("23").load().migrate();
        var db=BundleCoreTestSupport.DB;
        UUID actor=UUID.randomUUID(),customer=UUID.randomUUID();
        String columns="quotation_id,quotation_code,revision_no,customer_id,project_name,quotation_date,valid_until,created_by";
        String common="'V24-Q',1,'"+customer+"','Migration',current_date,current_date+1,'"+actor+"'";
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            sql.execute("insert into users(id,username,email,password_hash,status) values('"+actor+"','v24-user','v24@example.invalid','{disabled-test-account}','ACTIVE')");
            sql.execute("insert into customers(customer_id,customer_code,customer_name,phone) values('"+customer+"','V24-C','Migration','0900000000')");
            sql.execute("insert into quotations("+columns+") values('"+UUID.randomUUID()+"',"+common+")");
        }
        assertEquals(1,config(url).target("24").load().migrate().migrationsExecuted);
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            sql.execute("insert into quotations("+columns+") values('"+UUID.randomUUID()+"',"+common.replace("'V24-Q',1,","'V24-Q',2,")+")");
            assertThrows(java.sql.SQLException.class,() -> sql.execute("insert into quotations("+columns+") values('"+UUID.randomUUID()+"',"+common+")"));
            try (var row=sql.executeQuery("select count(*) from quotations where quotation_code='V24-Q'")) {
                assertTrue(row.next()); assertEquals(2,row.getInt(1));
            }
        }
    }

    @Test
    void v22PreservesExistingOrdersAndDefaultsHoldingIntentToFalse() throws Exception {
        String url = database();
        config(url).target("21").load().migrate();
        var db = BundleCoreTestSupport.DB;
        UUID customer = UUID.randomUUID(), order = UUID.randomUUID();
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword())) {
            try (var insert = connection.prepareStatement("insert into customers(customer_id,customer_code,customer_name,phone) values(?,'V22-C','Migration','0900000000')")) {
                insert.setObject(1, customer);
                insert.executeUpdate();
            }
            try (var insert = connection.prepareStatement("insert into orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount,deposit_amount) values(?,'V22-O',?,'COUNTER','PROCESSING','UNPAID',now(),100,20)")) {
                insert.setObject(1, order);
                insert.setObject(2, customer);
                insert.executeUpdate();
            }
        }
        var upgraded = config(url).target("22").load();
        assertEquals(1, upgraded.migrate().migrationsExecuted);
        assertTrue(upgraded.validateWithResult().validationSuccessful);
        assertEquals(0, upgraded.migrate().migrationsExecuted);
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
             var query = connection.prepareStatement("select reserve_stock_after_deposit,total_amount,deposit_amount,payment_status from orders where order_id=?")) {
            query.setObject(1, order);
            try (var row = query.executeQuery()) {
                assertTrue(row.next());
                org.junit.jupiter.api.Assertions.assertFalse(row.getBoolean(1));
                assertEquals(0, row.getBigDecimal(2).compareTo(new java.math.BigDecimal("100")));
                assertEquals(0, row.getBigDecimal(3).compareTo(new java.math.BigDecimal("20")));
                assertEquals("UNPAID", row.getString(4));
            }
        }
    }

    @Test
    void v21RemovesUnusedRequiredColumnsAndPreservesQuotationItems() throws Exception {
        String url = database();
        config(url).target("20").load().migrate();
        var db = BundleCoreTestSupport.DB;
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
                var statement = connection.createStatement()) {
            UUID user = UUID.randomUUID(), customer = UUID.randomUUID(), category = UUID.randomUUID();
            UUID product = UUID.randomUUID(), variant = UUID.randomUUID(), quotation = UUID.randomUUID(), item = UUID.randomUUID();
            statement.execute("INSERT INTO users(id,username,email,password_hash,status) VALUES ('" + user
                    + "','quotation_migration','quotation-migration@example.invalid','{disabled-test-account}','ACTIVE')");
            statement.execute("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES ('" + customer
                    + "','QT-MIG','Migration','0900000000')");
            statement.execute("INSERT INTO categories(category_id,category_code,category_name) VALUES ('" + category + "','QT_MIG','Migration')");
            statement.execute("INSERT INTO products(product_id,product_code,product_name,category_id) VALUES ('" + product
                    + "','QT-MIG','Migration','" + category + "')");
            statement.execute("INSERT INTO product_variants(variant_id,product_id,sku,variant_name,unit) VALUES ('" + variant
                    + "','" + product + "','QT-MIG','Migration','piece')");
            statement.execute("INSERT INTO quotations(quotation_id,quotation_code,customer_id,project_name,quotation_date,valid_until,created_by) VALUES ('"
                    + quotation + "','QT-MIG','" + customer + "','Migration',current_date,current_date+1,'" + user + "')");
            String columns = "quotation_item_id,quotation_id,variant_id,quantity,unit_price,sku,unit";
            String values = "'" + item + "','" + quotation + "','" + variant + "',1,10,'QT-MIG','piece'";
            statement.execute("INSERT INTO quotation_items(" + columns + ",item_name_snapshot,net_unit_price) VALUES ("
                    + values + ",'Historical name',10)");

            assertEquals(1, config(url).target("21").load().migrate().migrationsExecuted);

            try (var rows = statement.executeQuery("SELECT count(*) FROM information_schema.columns WHERE table_schema='public' "
                    + "AND table_name='quotation_items' AND column_name IN ('item_name_snapshot','net_unit_price')")) {
                assertTrue(rows.next());
                assertEquals(0, rows.getInt(1));
            }
            try (var rows = statement.executeQuery("SELECT quantity,unit_price,sku FROM quotation_items WHERE quotation_item_id='" + item + "'")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getBigDecimal(1).intValue());
                assertEquals(10, rows.getBigDecimal(2).intValue());
                assertEquals("QT-MIG", rows.getString(3));
            }
            statement.execute("INSERT INTO quotation_items(" + columns + ") VALUES ("
                    + values.replace(item.toString(), UUID.randomUUID().toString()) + ")");
        }
    }

    @Test
    void retiredComboSeedIsRepairedWithoutDeletingCategory(@org.junit.jupiter.api.io.TempDir java.nio.file.Path legacy) throws Exception {
        var migrations = new org.springframework.core.io.ClassPathResource("db/migration").getFile().toPath();
        try (var files = java.nio.file.Files.list(migrations)) {
            for (var file : files.filter(p -> p.toString().endsWith(".sql")).toList()) {
                java.nio.file.Files.copy(file, legacy.resolve(file.getFileName()));
            }
        }
        java.nio.file.Files.writeString(legacy.resolve("V16__seed_system_combo_category.sql"), """
                INSERT INTO categories(category_id, category_code, category_name, status)
                VALUES (gen_random_uuid(), 'COMBO', 'Combo', 'ACTIVE');
                """);
        String url = database();
        config(url).locations("filesystem:" + legacy).load().migrate();
        var current = config(url).load();
        org.junit.jupiter.api.Assertions.assertFalse(current.validateWithResult().validationSuccessful);

        new com.aquasuite.ims.share.config.FlywayConfig().flywayMigrationStrategy().migrate(current);

        assertTrue(current.validateWithResult().validationSuccessful);
        var db = BundleCoreTestSupport.DB;
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM categories WHERE category_code='COMBO'")) {
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));
        }
    }

    @Test
    void creditProfileMigrationStopsForLegacyNullTermAndEnforcesValidTerms() throws Exception {
        String url = database();
        config(url).target("13").load().migrate();
        var db = BundleCoreTestSupport.DB;
        try (var connection = DriverManager.getConnection(url, db.getUsername(), db.getPassword());
                var statement = connection.createStatement()) {
            statement.execute("INSERT INTO public.customers (customer_id, customer_code, customer_name, phone, credit_limit, credit_term_days) "
                    + "VALUES ('10000000-0000-4000-8000-000000000099', 'MIG-CREDIT', 'Test', '0900000000', 100, NULL)");
            assertThrows(FlywayException.class, () -> config(url).load().migrate());
            try (var result = statement.executeQuery("SELECT credit_limit, credit_term_days FROM public.customers WHERE customer_code='MIG-CREDIT'")) {
                assertTrue(result.next());
                assertEquals(100, result.getBigDecimal(1).intValue());
                assertEquals(null, result.getObject(2));
            }
            statement.execute("UPDATE public.customers SET credit_term_days=30 WHERE customer_code='MIG-CREDIT'");
            config(url).load().migrate();
            assertThrows(java.sql.SQLException.class, () -> statement.execute("UPDATE public.customers SET credit_term_days=NULL WHERE customer_code='MIG-CREDIT'"));
            assertThrows(java.sql.SQLException.class, () -> statement.execute("UPDATE public.customers SET credit_term_days=31 WHERE customer_code='MIG-CREDIT'"));
            statement.execute("UPDATE public.customers SET credit_term_days=1 WHERE customer_code='MIG-CREDIT'");
        }
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
    void v20RecoversOnlyAnUnambiguousConfirmedWarehouseWithoutAllocatingStock() throws Exception {
        String url=database();
        config(url).target("19").load().migrate();
        var db=BundleCoreTestSupport.DB;
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword())) {
            UUID customer=UUID.randomUUID(),first=UUID.randomUUID(),ambiguous=UUID.randomUUID();
            try (var insert=connection.prepareStatement("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES (?,?,'Migration','0900000000')")) {
                insert.setObject(1,customer);insert.setString(2,"M-"+customer);insert.executeUpdate();
            }
            for (UUID order:java.util.List.of(first,ambiguous)) {
                try (var insert=connection.prepareStatement("INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) VALUES (?,?,?,'COUNTER','PROCESSING','UNPAID',now(),100)")) {
                    insert.setObject(1,order);insert.setString(2,"M-"+order);insert.setObject(3,customer);insert.executeUpdate();
                }
                try (var insert=connection.prepareStatement("INSERT INTO sales_reservation_requests(order_id,selection,pricing,reservation_value,actor_id) VALUES (?,'[]','[]',100,?)")) {
                    insert.setObject(1,order);insert.setObject(2,UUID.randomUUID());insert.executeUpdate();
                }
                for (String warehouse:order.equals(first) ? java.util.List.of("WH_MAIN") : java.util.List.of("WH_MAIN","WH_OTHER")) {
                    try (var insert=connection.prepareStatement("INSERT INTO sales_reservation_operations(request_id,order_id,payload,actor_id) VALUES (?,?,?,?)")) {
                        insert.setObject(1,UUID.randomUUID());insert.setObject(2,order);
                        insert.setString(3,"[\""+order+"\",\"REQUEST\",\""+warehouse+"\",[]]");
                        insert.setObject(4,UUID.randomUUID());insert.executeUpdate();
                    }
                }
            }
            assertEquals(1,config(url).target("20").load().migrate().migrationsExecuted);
            try (var query=connection.prepareStatement("SELECT warehouse_code FROM sales_reservation_requests WHERE order_id=?")) {
                query.setObject(1,first);
                try (var result=query.executeQuery()) { assertTrue(result.next());assertEquals("WH_MAIN",result.getString(1)); }
                query.setObject(1,ambiguous);
                try (var result=query.executeQuery()) { assertTrue(result.next());assertEquals(null,result.getString(1)); }
            }
            try (var query=connection.createStatement();var result=query.executeQuery("SELECT count(*) FROM sales_reservation_allocations")) {
                assertTrue(result.next());assertEquals(0,result.getInt(1));
            }
        }
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

    @Test
    void customerPhoneUniquenessUpgradePreservesHistoryAndRejectsDuplicates() throws Exception {
        String url=database();
        config(url).target("26").load().migrate();
        var db=BundleCoreTestSupport.DB;
        UUID customer=UUID.randomUUID(), duplicate=UUID.randomUUID(), order=UUID.randomUUID();
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            sql.execute("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES('"+customer+"','LINK-A','First','0901234567'),('"+duplicate+"','LINK-B','Second','0901234567')");
            sql.execute("INSERT INTO orders(order_id,order_code,customer_id,order_channel,order_status,payment_status,order_date,total_amount) VALUES('"+order+"','LINK-ORDER','"+customer+"','IN_STORE','PROCESSING','UNPAID',current_timestamp,100)");
        }
        var upgrade=config(url).load();
        assertThrows(FlywayException.class,upgrade::migrate);
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            try (var row=sql.executeQuery("SELECT count(*) FROM customers WHERE phone='0901234567'")) {
                assertTrue(row.next()); assertEquals(2,row.getInt(1));
            }
            // Explicit fixture reconciliation, never performed by the migration itself.
            sql.execute("UPDATE customers SET phone='0907654321' WHERE customer_id='"+duplicate+"'");
        }
        assertEquals(1,upgrade.migrate().migrationsExecuted);
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            try (var row=sql.executeQuery("SELECT customer_id FROM orders WHERE order_id='"+order+"'")) {
                assertTrue(row.next()); assertEquals(customer,row.getObject(1,UUID.class));
            }
            assertThrows(java.sql.SQLException.class, () -> sql.execute("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES('"+UUID.randomUUID()+"','LINK-C','Third','0901234567')"));
        }
    }

    @Test
    void customerPhoneUpgradeRefusesWhitespaceWithoutChangingExistingData() throws Exception {
        String url=database();
        config(url).target("26").load().migrate();
        var db=BundleCoreTestSupport.DB;
        UUID customer=UUID.randomUUID();
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement()) {
            sql.execute("INSERT INTO customers(customer_id,customer_code,customer_name,phone) VALUES('"+customer+"','LINK-PAD','First',' 0901234567 ')");
        }
        assertThrows(FlywayException.class, () -> config(url).load().migrate());
        try (var connection=DriverManager.getConnection(url,db.getUsername(),db.getPassword()); var sql=connection.createStatement();
             var row=sql.executeQuery("SELECT phone FROM customers WHERE customer_id='"+customer+"'")) {
            assertTrue(row.next()); assertEquals(" 0901234567 ",row.getString(1));
        }
    }
}
