package com.aquasuite.ims.share.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class FlywayConfig {

    /**
     * Tự động chạy flyway.repair() trước khi migrate nhằm đồng bộ lại checksum giữa các môi trường
     * (tránh lỗi mismatch checksum do khác biệt CRLF / LF giữa Windows và Linux/Git).
     */
    @Bean
    public FlywayMigrationStrategy flywayMigrationStrategy() {
        return flyway -> {
            try {
                log.info("Executing Flyway repair to align schema history checksums...");
                flyway.repair();
            } catch (Exception e) {
                log.warn("Flyway repair encountered an issue (ignoring): {}", e.getMessage());
            }
            log.info("Executing Flyway migrate...");
            flyway.migrate();
        };
    }
}

