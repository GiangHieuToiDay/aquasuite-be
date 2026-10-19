package com.aquasuite.ims.config;

import org.springframework.context.annotation.Configuration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.Environment;

import static org.assertj.core.api.Assertions.assertThat;

class SpringProfileConfigDataTest {

    @Test
    void credentialsResolveFromEnvironmentAndSchemaDefaultsToValidate() {
        String databaseSecret = java.util.UUID.randomUUID().toString();
        String mailSecret = java.util.UUID.randomUUID().toString();
        var properties = new org.springframework.core.env.MapPropertySource(
                "isolated-credentials",
                java.util.Map.of(
                        "SPRING_DATASOURCE_URL", "jdbc:postgresql://localhost/test-config",
                        "SPRING_DATASOURCE_USERNAME", "test-config-user",
                        "SPRING_DATASOURCE_PASSWORD", databaseSecret,
                        "MAIL_USERNAME", "test-config-mail",
                        "MAIL_PASSWORD", mailSecret));
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .initializers(c -> c.getEnvironment().getPropertySources().addFirst(properties))
                .run()) {
            Environment env = context.getEnvironment();
            assertThat(env.getProperty("spring.datasource.url"))
                    .isEqualTo("jdbc:postgresql://localhost/test-config");
            assertThat(env.getProperty("spring.datasource.username")).isEqualTo("test-config-user");
            assertThat(env.getProperty("spring.datasource.password")).isEqualTo(databaseSecret);
            assertThat(env.getProperty("spring.mail.username")).isEqualTo("test-config-mail");
            assertThat(env.getProperty("spring.mail.password")).isEqualTo(mailSecret);
            assertThat(env.getProperty("spring.jpa.hibernate.ddl-auto")).isEqualTo("validate");
        }
    }

    // Exercise ConfigData without scanning business services or connecting to databases.
    @Configuration(proxyBeanMethods = false)
    static class ConfigOnlyApplication {
    }

    @Test
    @DisplayName("defaultLoadsCommonOnly: Không truyền profile -> default marker and common app name")
    void defaultLoadsCommonOnly() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
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
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
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
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
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
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
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
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(ConfigOnlyApplication.class)
                .web(WebApplicationType.NONE)
                .registerShutdownHook(false)
                .profiles("dev")
                .run("--aquasuite.config.environment=external-override")) {
            Environment env = context.getEnvironment();
            assertThat(env.getActiveProfiles()).containsExactly("dev");
            assertThat(env.getProperty("spring.application.name")).isEqualTo("aquasuite-be");
            assertThat(env.getProperty("aquasuite.config.environment")).isEqualTo("external-override");
        }
    }
}
