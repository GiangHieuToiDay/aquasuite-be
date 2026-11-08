package com.aquasuite.ims.module.finance.internal.notification;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SmtpDebtEmailNotificationPort implements DebtEmailNotificationPort {
    private final JavaMailSender mailSender;

    @Override
    public void send(String recipient, String subject, String body, byte[] attachment, String attachmentName) {
        MimeMessage message = mailSender.createMimeMessage();
        try {
            MimeMessageHelper helper = new MimeMessageHelper(message, attachment != null);
            helper.setTo(recipient);
            helper.setSubject(subject);
            helper.setText(body);
            if (attachment != null) {
                helper.addAttachment(attachmentName, new ByteArrayResource(attachment));
            }
            mailSender.send(message);
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to send debt email", exception);
        }
    }
}
