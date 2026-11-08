package com.aquasuite.ims.module.iam.internal.notification;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SmtpAccountCredentialsNotificationPortTest {

    @Test
    void sendsInitialCredentialsToTheAssignedEmailAddress() {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        SmtpAccountCredentialsNotificationPort notificationPort =
                new SmtpAccountCredentialsNotificationPort(mailSender);

        notificationPort.sendInitialCredentials(
                UUID.randomUUID(), "staff@example.com", "sales.one", "Temporary@123");

        ArgumentCaptor<SimpleMailMessage> message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("staff@example.com");
        assertThat(message.getValue().getSubject()).contains("staff account credentials");
        assertThat(message.getValue().getText())
                .contains("sales.one")
                .contains("Temporary@123");
    }
}
