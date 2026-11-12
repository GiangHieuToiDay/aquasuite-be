package com.aquasuite.ims.module.finance.internal.pdf;

import com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtDetailResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.InvoiceDebtResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentReceiptResponse;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.BaseFont;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.awt.Color;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

/** Generates an internal debt statement; it is not a legal tax invoice. */
@Service
public class StatementPdfService {
    private static final ZoneId VIETNAM_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(VIETNAM_ZONE);
    private static final Color HEADER_BLUE = new Color(30, 64, 175);
    private static final Color LIGHT_BLUE = new Color(239, 246, 255);
    private static final Color BORDER = new Color(148, 163, 184);

    public StatementDocument generate(CustomerDebtDetailResponse detail) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document(PageSize.A4, 32, 32, 30, 30);
            PdfWriter.getInstance(document, output);
            document.open();

            Fonts fonts = fonts();
            addStoreHeader(document, fonts);
            addCustomerInformation(document, detail, fonts);
            addInvoiceTable(document, detail.invoices(), fonts);
            addReceiptTable(document, detail.verifiedReceipts(), fonts);
            addSummary(document, detail, fonts);
            addSignatures(document, fonts);

            document.close();
            return new StatementDocument("sao-ke-cong-no-" + detail.customer().customerCode() + ".pdf", output.toByteArray());
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate statement PDF", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to close statement PDF output", exception);
        }
    }

    private void addStoreHeader(Document document, Fonts fonts) throws DocumentException {
        Paragraph brand = new Paragraph("AQUASUITE", fonts.brand());
        brand.setAlignment(Element.ALIGN_CENTER);
        document.add(brand);

        Paragraph tagline = new Paragraph("Hệ thống quản lý cửa hàng thiết bị vệ sinh", fonts.small());
        tagline.setAlignment(Element.ALIGN_CENTER);
        document.add(tagline);

        Paragraph title = new Paragraph("SAO KÊ CÔNG NỢ KHÁCH HÀNG", fonts.title());
        title.setSpacingBefore(12);
        title.setAlignment(Element.ALIGN_CENTER);
        document.add(title);

        Paragraph generatedAt = new Paragraph("Ngày lập: " + DATE_TIME_FORMAT.format(java.time.Instant.now()), fonts.small());
        generatedAt.setAlignment(Element.ALIGN_CENTER);
        generatedAt.setSpacingAfter(10);
        document.add(generatedAt);
    }

    private void addCustomerInformation(Document document, CustomerDebtDetailResponse detail, Fonts fonts) throws DocumentException {
        PdfPTable info = new PdfPTable(new float[] {1.05f, 1.85f, 1.05f, 1.85f});
        info.setWidthPercentage(100);
        info.setSpacingAfter(12);
        info.addCell(labelCell("Mã khách hàng", fonts));
        info.addCell(valueCell(detail.customer().customerCode(), fonts));
        info.addCell(labelCell("Tên khách hàng", fonts));
        info.addCell(valueCell(detail.customer().customerName(), fonts));
        info.addCell(labelCell("Số điện thoại", fonts));
        info.addCell(valueCell(detail.customer().phone(), fonts));
        info.addCell(labelCell("Email", fonts));
        info.addCell(valueCell(detail.customer().email(), fonts));
        info.addCell(labelCell("Nhóm khách hàng", fonts));
        info.addCell(valueCell(detail.customer().customerType(), fonts));
        info.addCell(labelCell("Kỳ hạn tín dụng", fonts));
        info.addCell(valueCell(detail.customer().creditTermDays() == null ? "—" : detail.customer().creditTermDays() + " ngày", fonts));
        document.add(info);
    }

    private void addInvoiceTable(Document document, List<InvoiceDebtResponse> invoices, Fonts fonts) throws DocumentException {
        sectionTitle(document, "CHI TIẾT HÓA ĐƠN CÒN THEO DÕI", fonts);
        PdfPTable table = new PdfPTable(new float[] {0.45f, 1.35f, 1.05f, 1.0f, 1.25f, 1.15f, 1.15f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("STT", "Mã hóa đơn", "Ngày phát hành", "Hạn thanh toán", "Tổng tiền", "Đã trả", "Còn nợ")) {
            table.addCell(headerCell(header, fonts));
        }
        if (invoices.isEmpty()) {
            PdfPCell empty = bodyCell("Chưa có hóa đơn điện tử được phát hành cho khách hàng.", fonts.normal(), Element.ALIGN_CENTER);
            empty.setColspan(7);
            table.addCell(empty);
        } else {
            for (int index = 0; index < invoices.size(); index++) {
                InvoiceDebtResponse invoice = invoices.get(index);
                table.addCell(bodyCell(String.valueOf(index + 1), fonts.normal(), Element.ALIGN_CENTER));
                table.addCell(bodyCell(invoice.invoiceCode(), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(invoice.issueDate() == null ? "—" : DATE_TIME_FORMAT.format(invoice.issueDate()), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(formatDate(invoice.dueDate()), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(money(invoice.totalAmount()), fonts.normal(), Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(invoice.verifiedPaidAmount()), fonts.normal(), Element.ALIGN_RIGHT));
                table.addCell(bodyCell(money(invoice.outstandingAmount()), fonts.normal(), Element.ALIGN_RIGHT));
            }
        }
        document.add(table);
    }

    private void addReceiptTable(Document document, List<PaymentReceiptResponse> receipts, Fonts fonts) throws DocumentException {
        sectionTitle(document, "CÁC KHOẢN THU ĐÃ XÁC MINH", fonts);
        PdfPTable table = new PdfPTable(new float[] {0.55f, 1.35f, 1.25f, 1.6f, 1.2f});
        table.setWidthPercentage(100);
        table.setHeaderRows(1);
        for (String header : List.of("STT", "Mã giao dịch", "Ngày thu", "Phương thức", "Số tiền")) {
            table.addCell(headerCell(header, fonts));
        }
        if (receipts.isEmpty()) {
            PdfPCell empty = bodyCell("Chưa có khoản thu đã xác minh.", fonts.normal(), Element.ALIGN_CENTER);
            empty.setColspan(5);
            table.addCell(empty);
        } else {
            for (int index = 0; index < receipts.size(); index++) {
                PaymentReceiptResponse receipt = receipts.get(index);
                table.addCell(bodyCell(String.valueOf(index + 1), fonts.normal(), Element.ALIGN_CENTER));
                table.addCell(bodyCell(receipt.transactionCode(), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(receipt.transactionDate() == null ? "—" : DATE_TIME_FORMAT.format(receipt.transactionDate()), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(receipt.paymentMethod(), fonts.normal(), Element.ALIGN_LEFT));
                table.addCell(bodyCell(money(receipt.amount()), fonts.normal(), Element.ALIGN_RIGHT));
            }
        }
        document.add(table);
    }

    private void addSummary(Document document, CustomerDebtDetailResponse detail, Fonts fonts) throws DocumentException {
        PdfPTable summary = new PdfPTable(new float[] {3.0f, 1.4f});
        summary.setWidthPercentage(62);
        summary.setHorizontalAlignment(Element.ALIGN_RIGHT);
        summary.setSpacingBefore(12);
        summary.addCell(summaryLabel("Dư nợ hóa đơn đã phát hành", fonts.normal()));
        summary.addCell(summaryValue(money(detail.summary().issuedInvoiceReceivable()), fonts.normal()));
        summary.addCell(summaryLabel("Khoản thu đơn chưa phân bổ", fonts.normal()));
        summary.addCell(summaryValue(money(detail.unallocatedVerifiedReceiptAmount()), fonts.normal()));
        summary.addCell(summaryLabel("Nợ quá hạn", fonts.normal()));
        summary.addCell(summaryValue(money(detail.summary().overdueBalance()), fonts.normal()));
        summary.addCell(summaryLabel("Hạn mức tín dụng còn lại", fonts.normal()));
        summary.addCell(summaryValue(money(detail.summary().availableCredit()), fonts.normal()));
        summary.addCell(summaryLabel("TỔNG DƯ NỢ HIỆN TẠI", fonts.bold()));
        summary.addCell(summaryValue(money(detail.summary().outstandingBalance()), fonts.bold()));
        document.add(summary);

        Paragraph note = new Paragraph("Ghi chú: Đây là sao kê công nợ nội bộ, không thay thế hóa đơn điện tử hoặc chứng từ thanh toán hợp pháp.", fonts.small());
        note.setSpacingBefore(10);
        document.add(note);
    }

    private void addSignatures(Document document, Fonts fonts) throws DocumentException {
        PdfPTable signatures = new PdfPTable(3);
        signatures.setWidthPercentage(100);
        signatures.setSpacingBefore(28);
        for (String role : List.of("NGƯỜI LẬP", "KẾ TOÁN", "KHÁCH HÀNG")) {
            PdfPCell cell = new PdfPCell();
            cell.setBorder(PdfPCell.NO_BORDER);
            cell.setHorizontalAlignment(Element.ALIGN_CENTER);
            cell.setMinimumHeight(68);
            cell.addElement(new Paragraph(role, fonts.bold()));
            Paragraph hint = new Paragraph("(Ký, họ tên)", fonts.small());
            hint.setAlignment(Element.ALIGN_CENTER);
            cell.addElement(hint);
            signatures.addCell(cell);
        }
        document.add(signatures);
    }

    private void sectionTitle(Document document, String title, Fonts fonts) throws DocumentException {
        Paragraph paragraph = new Paragraph(title, fonts.bold());
        paragraph.setSpacingBefore(12);
        paragraph.setSpacingAfter(5);
        document.add(paragraph);
    }

    private PdfPCell labelCell(String value, Fonts fonts) {
        PdfPCell cell = bodyCell(value, fonts.smallBold(), Element.ALIGN_LEFT);
        cell.setBackgroundColor(LIGHT_BLUE);
        return cell;
    }

    private PdfPCell valueCell(String value, Fonts fonts) {
        return bodyCell(blank(value), fonts.small(), Element.ALIGN_LEFT);
    }

    private PdfPCell headerCell(String value, Fonts fonts) {
        PdfPCell cell = bodyCell(value, fonts.header(), Element.ALIGN_CENTER);
        cell.setBackgroundColor(HEADER_BLUE);
        return cell;
    }

    private PdfPCell summaryLabel(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(value, font));
        cell.setPadding(5);
        cell.setBorderColor(BORDER);
        cell.setHorizontalAlignment(Element.ALIGN_LEFT);
        return cell;
    }

    private PdfPCell summaryValue(String value, Font font) {
        PdfPCell cell = summaryLabel(value, font);
        cell.setHorizontalAlignment(Element.ALIGN_RIGHT);
        return cell;
    }

    private PdfPCell bodyCell(String value, Font font, int alignment) {
        PdfPCell cell = new PdfPCell(new Phrase(blank(value), font));
        cell.setPadding(4);
        cell.setBorderColor(BORDER);
        cell.setVerticalAlignment(Element.ALIGN_MIDDLE);
        cell.setHorizontalAlignment(alignment);
        return cell;
    }

    private String formatDate(LocalDate date) {
        return date == null ? "—" : DATE_FORMAT.format(date);
    }

    private String money(BigDecimal value) {
        NumberFormat formatter = NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN"));
        formatter.setMaximumFractionDigits(0);
        formatter.setMinimumFractionDigits(0);
        return formatter.format(value == null ? BigDecimal.ZERO : value) + " đ";
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    private Fonts fonts() {
        try {
            BaseFont base = BaseFont.createFont(resolveFontPath(), BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            return new Fonts(new Font(base, 9), new Font(base, 9, Font.BOLD), new Font(base, 8),
                    new Font(base, 8, Font.BOLD), new Font(base, 9, Font.BOLD, Color.WHITE),
                    new Font(base, 14, Font.BOLD), new Font(base, 16, Font.BOLD, HEADER_BLUE));
        } catch (Exception exception) {
            throw new IllegalStateException("A Vietnamese-capable PDF font is required to generate customer statements.", exception);
        }
    }

    private String resolveFontPath() {
        String configured = System.getenv("AQUASUITE_PDF_FONT_PATH");
        List<String> candidates = List.of(
                configured == null ? "" : configured,
                "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf",
                "C:\\Windows\\Fonts\\arial.ttf");
        return candidates.stream().filter(path -> !path.isBlank()).filter(path -> new File(path).isFile()).findFirst()
                .orElseThrow(() -> new IllegalStateException("No compatible PDF font found; configure AQUASUITE_PDF_FONT_PATH."));
    }

    private record Fonts(Font normal, Font bold, Font small, Font smallBold, Font header, Font title, Font brand) {}
}
