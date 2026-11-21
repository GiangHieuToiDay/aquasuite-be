package com.aquasuite.ims.integration.iam;

import com.aquasuite.ims.exception.iam.RegistrationErrorCode;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class RegistrationEmailSenderTest {
    @Test void sendsRegistrationCodeToEmail() {
        var transport = mock(JavaMailSender.class);
        var sender = new RegistrationEmailSender(transport);
        sender.sendRegistrationOtp("customer@example.test", "012345");
        var message = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(transport).send(message.capture());
        assertThat(message.getValue().getTo()).containsExactly("customer@example.test");
        assertThat(message.getValue().getSubject()).contains("Registration");
        assertThat(message.getValue().getText()).contains("012345", "5 minutes");
    }

    @Test void smtpFailureReturnsMailUnavailableWithoutProviderDetails() {
        var transport = mock(JavaMailSender.class);
        doThrow(new MailSendException("synthetic failure")).when(transport).send(any(SimpleMailMessage.class));
        assertThatThrownBy(() -> new RegistrationEmailSender(transport)
                .sendRegistrationOtp("customer@example.test", "012345"))
                .isInstanceOfSatisfying(AppException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo(RegistrationErrorCode.MAIL_UNAVAILABLE));
    }
}
