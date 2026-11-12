package com.aquasuite.ims.module.iam.internal.service.otp;

import com.aquasuite.ims.module.iam.internal.error.RegistrationErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * Adapter Twilio Verify cho SMS OTP đăng ký, dùng credential từ cấu hình môi trường và timeout hữu hạn.
 * Không log request/response của provider vì có thể chứa credential, SĐT hoặc OTP.
 */
@Component
public class TwilioVerifyClient {
    private final RestClient client;
    private final String serviceSid;
    private final boolean configured;

    /** Khởi tạo HTTPS client dùng Basic Auth và kiểm tra định dạng Account SID/Verify Service SID. */
    @Autowired
    public TwilioVerifyClient(RestClient.Builder builder,
            @Value("${aquasuite.iam.twilio.account-sid:}") String accountSid,
            @Value("${aquasuite.iam.twilio.auth-token:}") String authToken,
            @Value("${aquasuite.iam.twilio.verify-service-sid:}") String serviceSid) {
        this.serviceSid = serviceSid;
        this.configured = accountSid.matches("AC[0-9a-fA-F]{32}")
                && !authToken.isBlank() && serviceSid.matches("VA[0-9a-fA-F]{32}");
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        this.client = builder.clone().baseUrl("https://verify.twilio.com/v2")
                .requestFactory(factory)
                .defaultHeaders(headers -> headers.setBasicAuth(accountSid, authToken)).build();
    }

    /** Cho phép truyền HTTP client giả lập khi kiểm thử mà không gọi Twilio thật. */
    TwilioVerifyClient(RestClient client, String serviceSid) {
        this.client = client;
        this.serviceSid = serviceSid;
        this.configured = true;
    }

    /** Gửi OTP qua kênh SMS tới SĐT Việt Nam và trả Verification SID của yêu cầu đang pending. */
    public String send(String phone) {
        var form = new LinkedMultiValueMap<String, String>();
        form.add("To", vietnamE164(phone));
        form.add("Channel", "sms");
        Verification result = post("Verifications", form, false);
        if (result == null || result.sid() == null || !result.sid().matches("VE[0-9a-fA-F]{32}")
                || !"pending".equals(result.status())) {
            throw new AppException(RegistrationErrorCode.SMS_UNAVAILABLE);
        }
        return result.sid();
    }

    /** Chỉ chấp nhận approved thuộc đúng Verification SID đã lưu, không tra phiên mới nhất bằng SĐT. */
    public boolean check(String verificationSid, String otp) {
        if (verificationSid == null || !verificationSid.matches("VE[0-9a-fA-F]{32}")) return false;
        var form = new LinkedMultiValueMap<String, String>();
        // Bind verification to this challenge rather than looking up the latest code by phone.
        form.add("VerificationSid", verificationSid);
        form.add("Code", otp);
        Verification result = post("VerificationCheck", form, true);
        return result != null && verificationSid.equals(result.sid()) && "approved".equals(result.status());
    }

    /**
     * Gọi Verify API bằng form URL-encoded; ánh xạ lỗi mà không trả nội dung nhạy cảm của provider.
     * HTTP 429 thành lỗi giới hạn; 404 khi kiểm tra được coi là verification không còn hợp lệ.
     */
    private Verification post(String resource, LinkedMultiValueMap<String, String> form, boolean checking) {
        if (!configured) throw new AppException(RegistrationErrorCode.SMS_UNAVAILABLE);
        try {
            return client.post().uri("/Services/{serviceSid}/{resource}", serviceSid, resource)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(Verification.class);
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 429) throw new AppException(RegistrationErrorCode.OTP_LIMIT);
            if (checking && ex.getStatusCode().value() == 404) return null;
            throw new AppException(RegistrationErrorCode.SMS_UNAVAILABLE);
        } catch (RestClientException ex) {
            throw new AppException(RegistrationErrorCode.SMS_UNAVAILABLE);
        }
    }

    /** Chuyển số di động Việt Nam 10 chữ số đầu 0 sang E.164 bằng cách thay số 0 bằng +84. */
    static String vietnamE164(String phone) {
        if (phone == null || !phone.matches("0[35789][0-9]{8}")) {
            throw new AppException(RegistrationErrorCode.SMS_UNAVAILABLE);
        }
        return "+84" + phone.substring(1);
    }

    /** Chỉ đọc SID và trạng thái cần thiết từ phản hồi Twilio, bỏ qua các trường khác. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Verification(String sid, String status) {}
}
