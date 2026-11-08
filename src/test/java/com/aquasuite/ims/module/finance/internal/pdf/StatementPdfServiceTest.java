package com.aquasuite.ims.module.finance.internal.pdf;

import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.finance.internal.payload.DebtStatus;
import com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtDetailResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtSummaryResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.InvoiceDebtResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentReceiptResponse;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatementPdfServiceTest {
    @Test
    void generatesPrintableVietnameseDebtStatement() throws Exception {
        UUID customerId = UUID.fromString("10000000-0000-4000-8000-000000000001");
        CustomerDebtDetailResponse detail = new CustomerDebtDetailResponse(
                new CustomerDebtProfileDto(customerId, "KH-001", "Công ty An Khang", "0903118240",
                        "ketoan@ankhang.vn", "CONTRACTOR", "0312456789", "Công ty CP Xây dựng An Khang",
                        List.of("Bắc Ninh"), List.of("Riverside"), BigDecimal.valueOf(500_000_000), 30, "ACTIVE"),
                CustomerDebtSummaryResponse.builder().customerId(customerId).customerCode("KH-001")
                        .customerName("Công ty An Khang").creditLimit(BigDecimal.valueOf(500_000_000))
                        .issuedInvoiceReceivable(BigDecimal.valueOf(180_000_000))
                        .outstandingBalance(BigDecimal.valueOf(180_000_000)).overdueBalance(BigDecimal.ZERO)
                        .availableCredit(BigDecimal.valueOf(320_000_000)).debtStatus(DebtStatus.WITHIN_TERM).build(),
                List.of(InvoiceDebtResponse.builder().invoiceId(UUID.randomUUID()).invoiceCode("INV-2508")
                        .issueDate(OffsetDateTime.of(2026, 11, 1, 10, 0, 0, 0, ZoneOffset.UTC))
                        .dueDate(LocalDate.of(2026, 12, 1)).totalAmount(BigDecimal.valueOf(120_000_000))
                        .verifiedPaidAmount(BigDecimal.valueOf(40_000_000)).outstandingAmount(BigDecimal.valueOf(80_000_000)).build()),
                List.of(),
                List.of(PaymentReceiptResponse.builder().transactionId(UUID.randomUUID()).transactionCode("TXT-9001")
                        .amount(BigDecimal.valueOf(40_000_000)).paymentMethod("BANK_TRANSFER")
                        .transactionDate(OffsetDateTime.now(ZoneOffset.UTC)).status("VERIFIED").build()),
                List.of(), BigDecimal.ZERO, List.of());

        StatementDocument document = new StatementPdfService().generate(detail);

        String previewPath = System.getProperty("statement.pdf.preview");
        if (previewPath != null && !previewPath.isBlank()) {
            Files.write(Path.of(previewPath), document.bytes());
        }

        assertEquals("sao-ke-cong-no-KH-001.pdf", document.fileName());
        assertTrue(document.bytes().length > 1_000);
        assertTrue(new String(document.bytes(), 0, 4, StandardCharsets.US_ASCII).startsWith("%PDF"));
    }
}
