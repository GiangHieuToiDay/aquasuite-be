package com.aquasuite.ims.module.iam.internal.notification;

import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SmtpAccountCredentialsNotificationPort implements AccountCredentialsNotificationPort {

    private final JavaMailSender mailSender;

    @Override
    public void sendInitialCredentials(UUID userId, String email, String username, String temporaryPassword) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(email);
        message.setSubject("[AquaSuite] Your staff account credentials");
        message.setText("""
                Your AquaSuite staff account has been created.

                Username: %s
                Temporary password: %s

                Please keep these credentials secure.
                """.formatted(username, temporaryPassword));
        mailSender.send(message);
    }
}
