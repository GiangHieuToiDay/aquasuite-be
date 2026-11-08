package com.aquasuite.ims.share.config.mail;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import java.net.ServerSocket;
import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

class MailConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
            .withUserConfiguration(MailConfig.class);

    @Test void boundsSmtpByDefaultAndHonorsOverrides() {
        context.run(app -> {
            var props = app.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("5000");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("3000");
            assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("5000");
        });
        context.withPropertyValues("spring.mail.properties.mail.smtp.connectiontimeout=800",
                "spring.mail.properties.mail.smtp.timeout=900",
                "spring.mail.properties.mail.smtp.writetimeout=1000").run(app -> {
            var props = app.getBean(JavaMailSenderImpl.class).getJavaMailProperties();
            assertThat(props.getProperty("mail.smtp.connectiontimeout")).isEqualTo("800");
            assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("900");
            assertThat(props.getProperty("mail.smtp.writetimeout")).isEqualTo("1000");
        });
    }

    @Test void silentSmtpServerCannotBlockRegistrationIndefinitely() throws Exception {
        var release = new CountDownLatch(1);
        try (var pool = Executors.newSingleThreadExecutor(); var server = new ServerSocket(0)) {
            server.setSoTimeout(2000);
            var accepted = pool.submit(() -> {
                try (var socket = server.accept()) {
                    release.await(5, TimeUnit.SECONDS);
                }
                return null;
            });
            try {
                context.withPropertyValues("spring.mail.host=127.0.0.1",
                        "spring.mail.properties.mail.smtp.auth=false",
                        "spring.mail.port=" + server.getLocalPort(),
                        "spring.mail.properties.mail.smtp.timeout=100").run(app ->
                        assertTimeoutPreemptively(Duration.ofSeconds(3), () ->
                                assertThatThrownBy(app.getBean(JavaMailSenderImpl.class)::testConnection)
                                        .isInstanceOf(jakarta.mail.MessagingException.class)
                                        .hasStackTraceContaining("timed out")));
            } finally {
                release.countDown();
            }
            accepted.get(6, TimeUnit.SECONDS);
        }
    }
}
