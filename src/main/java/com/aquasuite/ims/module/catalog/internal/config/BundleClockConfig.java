package com.aquasuite.ims.module.catalog.internal.config;

import org.springframework.context.annotation.*;

import java.time.Clock;

@Configuration
public class BundleClockConfig {
    @Bean
    @Primary
    public Clock bundleClock() {
        return Clock.systemUTC();
    }
}
