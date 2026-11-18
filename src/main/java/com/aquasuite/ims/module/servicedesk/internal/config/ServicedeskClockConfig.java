package com.aquasuite.ims.module.servicedesk.internal.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;
import java.time.ZoneId;

@Configuration
public class ServicedeskClockConfig {

    public static final ZoneId STORE_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean("servicedeskClock")
    public Clock servicedeskClock() {
        return Clock.system(STORE_ZONE);
    }
}

