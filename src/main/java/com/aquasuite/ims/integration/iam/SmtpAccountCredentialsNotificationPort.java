package com.aquasuite.ims.integration.iam;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;
import org.springframework.web.util.HtmlUtils;

import java.nio.charset.StandardCharsets;
import java.time.Year;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SmtpAccountCredentialsNotificationPort implements AccountCredentialsNotificationPort {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:noreply@thamvanhome.vn}")
    private String fromAddress;

    @Value("${app.frontend.login-url:http://localhost:5173/login}")
    private String loginUrl;

    @Override
    public void sendInitialCredentials(UUID userId, String email, String username, String temporaryPassword) {
        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    mimeMessage,
                    MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED,
                    StandardCharsets.UTF_8.name()
            );

            helper.setFrom(fromAddress, "Thám Vân Home — AquaSuite IMS");
            helper.setTo(email);
            helper.setSubject("[Thám Vân Home] Thông tin kích hoạt tài khoản nhân sự — AquaSuite");

            String htmlContent = buildHtmlTemplate(
                    HtmlUtils.htmlEscape(username),
                    HtmlUtils.htmlEscape(temporaryPassword),
                    HtmlUtils.htmlEscape(loginUrl)
            );
            String plainTextFallback = buildPlainTextFallback(username, temporaryPassword, loginUrl);

            helper.setText(plainTextFallback, htmlContent);
            mailSender.send(mimeMessage);

            log.info("Sent initial account credentials email to userId={}, email={}", userId, email);
        } catch (MessagingException | java.io.UnsupportedEncodingException ex) {
            log.error("Failed to send credentials email to userId={}, email={}", userId, email, ex);
            throw new IllegalStateException("Không thể gửi email thông tin đăng nhập cho nhân viên.", ex);
        }
    }

    private String buildPlainTextFallback(String username, String temporaryPassword, String url) {
        return """
                THÁM VÂN HOME — HỆ THỐNG QUẢN TRỊ AQUASUITE
                ==================================================
                Chào mừng bạn gia nhập đội ngũ Thám Vân Home!

                Tài khoản nhân sự của bạn trên hệ thống quản trị bán hàng & kho vận AquaSuite đã được khởi tạo thành công.

                - Tên đăng nhập: %s
                - Mật khẩu tạm thời: %s
                - Đường dẫn đăng nhập: %s

                Lưu ý bảo mật: Vui lòng đăng nhập và đổi mật khẩu ngay trong lần truy cập đầu tiên. Tuyệt đối không chia sẻ thông tin này cho người khác.
                """.formatted(username, temporaryPassword, url);
    }

    private String buildHtmlTemplate(String safeUsername, String safePassword, String safeLoginUrl) {
        int currentYear = Year.now().getValue();
        return """
                <!DOCTYPE html>
                <html lang="vi">
                <head>
                    <meta charset="UTF-8">
                    <meta name="viewport" content="width=device-width, initial-scale=1.0">
                    <title>Kích hoạt tài khoản nhân sự — Thám Vân Home</title>
                </head>
                <body style="margin:0;padding:0;background-color:#f1f5f9;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e293b;">
                    <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f1f5f9;padding:32px 16px;">
                        <tr>
                            <td align="center">
                                <!-- Main Container -->
                                <table role="presentation" width="600" cellspacing="0" cellpadding="0" border="0" style="max-width:600px;width:100%%;background-color:#ffffff;border-radius:12px;overflow:hidden;border:1px solid #e2e8f0;box-shadow:0 4px 12px rgba(15,23,42,0.05);">
                                    
                                    <!-- Header Brand -->
                                    <tr>
                                        <td style="background:linear-gradient(135deg,#0c4a6e 0%%,#0369a1 100%%);padding:28px 32px;text-align:left;">
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                                <tr>
                                                    <td>
                                                        <p style="margin:0;font-size:11px;font-weight:700;letter-spacing:1.5px;text-transform:uppercase;color:#bae6fd;">
                                                            THIẾT BỊ VỆ SINH &amp; NỘI THẤT PHÒNG TẮM
                                                        </p>
                                                        <h1 style="margin:6px 0 0;font-size:22px;font-weight:700;color:#ffffff;letter-spacing:-0.3px;">
                                                            THÁM VÂN HOME
                                                        </h1>
                                                    </td>
                                                    <td align="right" valign="middle">
                                                        <span style="display:inline-block;background-color:rgba(255,255,255,0.14);border:1px solid rgba(255,255,255,0.28);border-radius:6px;padding:6px 12px;font-size:12px;font-weight:600;color:#f0f9ff;">
                                                            AquaSuite IMS
                                                        </span>
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <!-- Body Content -->
                                    <tr>
                                        <td style="padding:32px;">
                                            <h2 style="margin:0 0 12px;font-size:18px;font-weight:700;color:#0f172a;">
                                                Chào mừng bạn gia nhập Thám Vân Home!
                                            </h2>
                                            <p style="margin:0 0 20px;font-size:14px;line-height:1.6;color:#475569;">
                                                Tài khoản nhân sự của bạn trên hệ thống quản trị kho vận và bán hàng <strong>AquaSuite</strong> đã được khởi tạo. Dưới đây là thông tin đăng nhập ban đầu dành riêng cho bạn:
                                            </p>

                                            <!-- Credentials Box -->
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#f8fafc;border:1px solid #cbd5e1;border-radius:8px;margin-bottom:24px;">
                                                <tr>
                                                    <td style="padding:20px 24px;">
                                                        <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0">
                                                            <tr>
                                                                <td style="padding-bottom:12px;border-bottom:1px dashed #cbd5e1;">
                                                                    <span style="display:block;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.6px;color:#64748b;">
                                                                        Tên đăng nhập (Username)
                                                                    </span>
                                                                    <span style="display:inline-block;margin-top:4px;font-family:'Consolas','Courier New',monospace;font-size:16px;font-weight:700;color:#0369a1;">
                                                                        %s
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                            <tr>
                                                                <td style="padding-top:12px;">
                                                                    <span style="display:block;font-size:11px;font-weight:700;text-transform:uppercase;letter-spacing:0.6px;color:#64748b;">
                                                                        Mật khẩu tạm thời
                                                                    </span>
                                                                    <span style="display:inline-block;margin-top:4px;background-color:#e0f2fe;border:1px solid #bae6fd;border-radius:4px;padding:4px 10px;font-family:'Consolas','Courier New',monospace;font-size:16px;font-weight:700;color:#0c4a6e;letter-spacing:0.5px;">
                                                                        %s
                                                                    </span>
                                                                </td>
                                                            </tr>
                                                        </table>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- CTA Button -->
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="margin-bottom:24px;">
                                                <tr>
                                                    <td align="center">
                                                        <a href="%s" target="_blank" style="display:inline-block;background-color:#0369a1;color:#ffffff;font-size:14px;font-weight:600;text-decoration:none;padding:12px 28px;border-radius:8px;">
                                                            Đăng nhập vào hệ thống &rarr;
                                                        </a>
                                                    </td>
                                                </tr>
                                            </table>

                                            <!-- Security Notice -->
                                            <table role="presentation" width="100%%" cellspacing="0" cellpadding="0" border="0" style="background-color:#fffbeb;border-left:4px solid #f59e0b;border-radius:6px;">
                                                <tr>
                                                    <td style="padding:14px 16px;font-size:13px;line-height:1.5;color:#78350f;">
                                                        <strong style="display:block;margin-bottom:4px;color:#92400e;">Lưu ý bảo mật quan trọng:</strong>
                                                        Vì lý do an toàn dữ liệu nội bộ, hệ thống yêu cầu bạn <strong>đổi mật khẩu ngay trong lần đăng nhập đầu tiên</strong>. Tuyệt đối không chia sẻ tài khoản hoặc mật khẩu này cho bất kỳ cá nhân nào khác.
                                                    </td>
                                                </tr>
                                            </table>
                                        </td>
                                    </tr>

                                    <!-- Footer -->
                                    <tr>
                                        <td style="background-color:#f8fafc;padding:20px 32px;border-top:1px solid #e2e8f0;text-align:center;">
                                            <p style="margin:0 0 6px;font-size:12px;font-weight:600;color:#334155;">
                                                Showroom Thiết Bị Vệ Sinh Thám Vân Home
                                            </p>
                                            <p style="margin:0 0 8px;font-size:11.5px;color:#64748b;">
                                                Đây là email tự động từ hệ thống quản trị nội bộ AquaSuite. Vui lòng không phản hồi trực tiếp email này.
                                            </p>
                                            <p style="margin:0;font-size:11px;color:#94a3b8;">
                                                &copy; %d Thám Vân Home. All rights reserved.
                                            </p>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                    </table>
                </body>
                </html>
                """.formatted(safeUsername, safePassword, safeLoginUrl, currentYear);
    }
}