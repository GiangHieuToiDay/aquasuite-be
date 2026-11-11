package com.aquasuite.ims.module.sales.internal.dev;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import java.time.Clock;

/** Explicit opt-in; never active together with the production profile. */
@Configuration
@Profile("uc29-demo & !prod & !production")
public class ProjectDepositDemoConfiguration {
    @Bean("projectDepositClock")
    Clock projectDepositClock() { return Clock.systemUTC(); }
}
