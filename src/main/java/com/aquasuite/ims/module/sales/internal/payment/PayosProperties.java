package com.aquasuite.ims.module.sales.internal.payment;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Cấu hình kết nối cổng thanh toán PayOS (Napas 24/7 VietQR).
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "payos")
public class PayosProperties {

    private String baseUrl = "https://api-merchant.payos.vn";
    private String clientId = "";
    private String apiKey = "";
    private String checksumKey = "";
    private String returnUrl = "http://localhost:5173/checkout/result";
    private String cancelUrl = "http://localhost:5173/checkout/result";
    private Long overrideAmount;

    /**
     * Kiểm tra bộ khóa cấu hình PayOS hợp lệ (không rỗng và không còn giá trị placeholder).
     */
    public boolean isConfigured() {
        return isValidCredential(clientId)
                && isValidCredential(apiKey)
                && isValidCredential(checksumKey);
    }

    private boolean isValidCredential(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }
        return !value.trim().startsWith("DAN_");
    }
}

