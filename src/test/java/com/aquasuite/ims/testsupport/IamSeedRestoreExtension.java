package com.aquasuite.ims.testsupport;

import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Các test IAM xóa roles/permissions/role_permissions để dựng dữ liệu riêng, làm mất dữ liệu seed
 * của Flyway và khiến test chạy sau (ví dụ test quyền catalog) bị 403. Extension này chụp lại
 * dữ liệu seed trước khi lớp test chạy và khôi phục sau khi lớp test kết thúc.
 */
public class IamSeedRestoreExtension implements BeforeAllCallback, AfterAllCallback {

    private static final String[] TABLES = {"roles", "permissions", "role_permissions"};
    private static final Map<String, List<Map<String, Object>>> SNAPSHOT = new java.util.LinkedHashMap<>();

    @Override
    public void beforeAll(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        synchronized (SNAPSHOT) {
            int current = count(jdbc);
            // Chỉ chụp lại khi có nhiều dòng hơn bản đã chụp, tránh ghi đè bằng dữ liệu đã bị xóa.
            if (current > snapshotSize()) {
                SNAPSHOT.clear();
                for (String table : TABLES) {
                    SNAPSHOT.put(table, jdbc.queryForList("SELECT * FROM public." + table));
                }
            }
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        JdbcTemplate jdbc = jdbc(context);
        synchronized (SNAPSHOT) {
            for (String table : TABLES) {
                for (Map<String, Object> row : SNAPSHOT.getOrDefault(table, List.of())) {
                    List<String> columns = new ArrayList<>(row.keySet());
                    String sql = "INSERT INTO public." + table + " (" + String.join(", ", columns) + ") VALUES ("
                            + "?, ".repeat(columns.size() - 1) + "?) ON CONFLICT DO NOTHING";
                    jdbc.update(sql, columns.stream().map(row::get).toArray());
                }
            }
        }
    }

    private static JdbcTemplate jdbc(ExtensionContext context) {
        return SpringExtension.getApplicationContext(context).getBean(JdbcTemplate.class);
    }

    private static int count(JdbcTemplate jdbc) {
        int total = 0;
        for (String table : TABLES) {
            total += jdbc.queryForObject("SELECT count(*) FROM public." + table, Integer.class);
        }
        return total;
    }

    private static int snapshotSize() {
        return SNAPSHOT.values().stream().mapToInt(List::size).sum();
    }
}
