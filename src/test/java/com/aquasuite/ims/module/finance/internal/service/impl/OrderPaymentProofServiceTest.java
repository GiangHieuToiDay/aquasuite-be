package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderPaymentProofServiceTest {
    @Test void validatesFileSignatureRatherThanClientContentType() {
        assertThat(OrderPaymentProofServiceImpl.detectType("%PDF-test".getBytes(StandardCharsets.US_ASCII))).isEqualTo("application/pdf");
        assertThatThrownBy(() -> OrderPaymentProofServiceImpl.detectType("<script>".getBytes(StandardCharsets.US_ASCII)))
                .isInstanceOf(AppException.class);
    }
}
