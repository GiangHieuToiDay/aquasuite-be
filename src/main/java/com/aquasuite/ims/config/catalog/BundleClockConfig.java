package com.aquasuite.ims.config.catalog;

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
