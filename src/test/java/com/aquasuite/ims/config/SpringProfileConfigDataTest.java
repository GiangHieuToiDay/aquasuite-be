package com.aquasuite.ims.config;

import com.aquasuite.ims.AquasuiteBeApplication;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class SpringProfileConfigDataTest {

    private static final String EXCLUDE_AUTOCONFIG =
            "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.orm.jpa.HibernateJpaAutoConfiguration";

    @Test
    @DisplayName("defaultLoadsCommonOnly: Không truyền profile -> default marker and common app name")
    void defaultLoadsCommonOnly() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AquasuiteBeApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .properties(EXCLUDE_AUTOCONFIG)
                .run()) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).isEmpty();
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("default");
        }
    }

    @Test
    @DisplayName("devLoadsAndOverridesCommon: Programmatic dev profile -> dev marker overrides default")
    void devLoadsAndOverridesCommon() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AquasuiteBeApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .properties(EXCLUDE_AUTOCONFIG)
                .profiles("dev")
                .run()) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("dev");
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("dev");
        }
    }

    @Test
    @DisplayName("testLoadsAndOverridesCommon: Programmatic test profile -> test marker overrides default")
    void testLoadsAndOverridesCommon() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AquasuiteBeApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .properties(EXCLUDE_AUTOCONFIG)
                .profiles("test")
                .run()) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("test");
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("test");
        }
    }

    @Test
    @DisplayName("prodLoadsAndOverridesCommon: Programmatic prod profile -> prod marker overrides default")
    void prodLoadsAndOverridesCommon() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AquasuiteBeApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .properties(EXCLUDE_AUTOCONFIG)
                .profiles("prod")
                .run()) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("prod");
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("prod");
        }
    }

    @Test
    @DisplayName("externalPropertyOverridesProfile: dev with command-line arg -> command-line overrides profile")
    void externalPropertyOverridesProfile() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(AquasuiteBeApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .properties(EXCLUDE_AUTOCONFIG)
                .profiles("dev")
                .run("--aquasuite.config.environment=external-override")) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("dev");
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("external-override");
        }
    }
}
