package com.aquasuite.ims.testsupport;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Xóa dữ liệu mà các test chạy ngoài transaction (Propagation.NOT_SUPPORTED) đã commit vào
 * PostgreSQL dùng chung, để không làm hỏng các test chạy sau. Không đụng tới dữ liệu seed
 * của Flyway (roles, permissions...).
 */
public final class CommittedFixtureCleaner {

    private static final String[] TABLES = {
        "sales_invoice_items", "sales_invoices", "order_modification_request_items",
        "order_modification_requests", "sales_reservation_releases", "order_items", "orders",
        "transactions", "inventories", "product_variants", "products", "categories",
        "customers", "users"
    };

    private CommittedFixtureCleaner() {}

    public static void clear(JdbcTemplate jdbc) {
        List<String> existing = new ArrayList<>();
        for (String table : TABLES) {
            if (jdbc.queryForObject("select to_regclass(?) is not null", Boolean.class, "public." + table)) {
                existing.add("public." + table);
            }
        }
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", existing) + " CASCADE");
    }
}
