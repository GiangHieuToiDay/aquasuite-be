package com.aquasuite.ims.integration.iam;

import jakarta.mail.Message;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpAccountCredentialsNotificationPortTest {

    @Test
    void sendsInitialCredentialsToTheAssignedEmailAddress() throws Exception {
        MimeMessage mimeMessage = new JavaMailSenderImpl().createMimeMessage();
        JavaMailSender mailSender = mock(JavaMailSender.class);
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        SmtpAccountCredentialsNotificationPort notificationPort =
                new SmtpAccountCredentialsNotificationPort(mailSender);
        ReflectionTestUtils.setField(notificationPort, "fromAddress", "noreply@example.com");
        ReflectionTestUtils.setField(notificationPort, "loginUrl", "http://localhost/login");

        notificationPort.sendInitialCredentials(
                UUID.randomUUID(), "staff@example.com", "sales.one", "Temporary@123");

        ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
        verify(mailSender).send(sent.capture());
        MimeMessage message = sent.getValue();
        message.saveChanges();

        assertThat(message.getRecipients(Message.RecipientType.TO)[0].toString())
                .isEqualTo("staff@example.com");
        assertThat(message.getSubject()).contains("kích hoạt tài khoản");

        ByteArrayOutputStream raw = new ByteArrayOutputStream();
        message.writeTo(raw);
        assertThat(raw.toString(java.nio.charset.StandardCharsets.UTF_8))
                .contains("sales.one")
                .contains("Temporary@123");
    }
}
