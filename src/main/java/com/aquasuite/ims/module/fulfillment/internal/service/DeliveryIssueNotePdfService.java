package com.aquasuite.ims.module.fulfillment.internal.service;

import com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode;
import com.aquasuite.ims.module.sales.api.OrderDispatchApi;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.openpdf.text.*;
import org.openpdf.text.pdf.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Printable goods issue record; reading it never changes inventory or payment state. */
@Service
@RequiredArgsConstructor
public class DeliveryIssueNotePdfService {
    private final DeliveryService deliveries;
    private final OrderDispatchApi orders;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    @Transactional(readOnly = true)
    public byte[] generate(UUID id) {
        // detail performs object-level authorization, including assigned-shipper ownership.
        var detail = deliveries.detail(id);
        var row = detail.delivery();
        if (row.confirmedAt() == null || List.of("DRAFT", "CANCELLED").contains(row.deliveryStatus()))
            throw new AppException(FulfillmentErrorCode.INVALID_STATE);
        var order = orders.read(row.orderId());
        var invoice = row.salesInvoiceId() == null ? null : orders.invoice(row.salesInvoiceId());
        try (var output = new ByteArrayOutputStream()) {
            String configured = System.getenv("AQUASUITE_PDF_FONT_PATH");
            String fontPath = List.of(configured == null ? "" : configured,
                    "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", "C:/Windows/Fonts/arial.ttf")
                    .stream().filter(p -> !p.isBlank() && Files.isRegularFile(Path.of(p)))
                    .findFirst().orElseThrow(() -> new IllegalStateException("Configure AQUASUITE_PDF_FONT_PATH"));
            BaseFont base = BaseFont.createFont(fontPath, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            Font normal = new Font(base, 10), bold = new Font(base, 10, Font.BOLD);
            Document document = new Document(PageSize.A4, 36, 36, 32, 42);
            PdfWriter writer = PdfWriter.getInstance(document, output);
            writer.setPageEvent(new PdfPageEventHelper() {
                @Override public void onEndPage(PdfWriter w, Document d) {
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_LEFT,
                            new Phrase(text(row.deliveryCode()), normal), d.left(), 24, 0);
                    ColumnText.showTextAligned(w.getDirectContent(), Element.ALIGN_RIGHT,
                            new Phrase("Trang " + w.getPageNumber(), normal), d.right(), 24, 0);
                }
            });
            document.open();
            Paragraph brand = new Paragraph("AQUASUITE", new Font(base, 15, Font.BOLD));
            document.add(brand);
            Paragraph title = new Paragraph("PHIẾU XUẤT KHO", new Font(base, 19, Font.BOLD));
            title.setAlignment(Element.ALIGN_CENTER); title.setSpacingAfter(14); document.add(title);
            document.add(new Paragraph("Số phiếu: " + text(row.deliveryCode()) + "   |   Đợt: " + row.batchNo(), bold));
            document.add(new Paragraph("Đơn hàng: " + text(order.orderCode()) + "   |   Hóa đơn: "
                    + (invoice == null ? "—" : text(invoice.invoiceCode())), normal));
            document.add(new Paragraph("Kho xuất: " + text(row.warehouseCode()) + "   |   Xác nhận xuất: " + date(row.confirmedAt()), normal));
            document.add(new Paragraph("Người nhận: " + text(row.recipientName()) + "   |   Điện thoại: " + text(row.recipientPhone()), normal));
            document.add(new Paragraph("Địa chỉ: " + text(row.deliveryAddress()), normal));
            document.add(new Paragraph("Shipper: " + text(detail.shipperName()) + "   |   Điện thoại: " + text(detail.shipperPhone()), normal));
            document.add(new Paragraph("Hẹn giao (giờ Việt Nam): " + date(row.scheduledDate()), normal));
            PdfPTable table = new PdfPTable(new float[]{0.5f, 1.5f, 3.4f, 0.9f, 1.7f});
            table.setWidthPercentage(100); table.setSpacingBefore(14); table.setSpacingAfter(12);
            table.setHeaderRows(1);
            for (String h : List.of("STT", "SKU", "Sản phẩm / biến thể", "SL xuất", "Serial")) {
                PdfPCell cell = cell(h, bold); cell.setBackgroundColor(new Color(235, 240, 245)); table.addCell(cell);
            }
            int index = 0;
            for (var item : detail.items()) {
                table.addCell(cell(String.valueOf(++index), normal));
                table.addCell(cell(item.sku(), normal));
                table.addCell(cell(text(item.productName()) + "\n" + text(item.variantName()), normal));
                table.addCell(cell(item.quantity().stripTrailingZeros().toPlainString(), normal));
                table.addCell(cell(item.serialNumbers() == null ? "—" : String.join(", ", item.serialNumbers()), normal));
            }
            document.add(table);
            String cod = Boolean.TRUE.equals(row.codRequired())
                    ? NumberFormat.getNumberInstance(Locale.forLanguageTag("vi-VN")).format(row.codAmount()) + " VND"
                    : "Không thu COD";
            document.add(new Paragraph("COD cần thu: " + cod, bold));
            document.add(new Paragraph("Ghi chú: " + text(row.note()), normal));
            document.add(new Paragraph("Phiếu dùng để xuất kho và bàn giao hàng; không xác nhận đã thu tiền.", normal));
            PdfPTable signatures = new PdfPTable(3); signatures.setWidthPercentage(100);
            signatures.setSpacingBefore(22); signatures.setKeepTogether(true);
            for (String role : List.of("NGƯỜI XUẤT KHO", "NGƯỜI VẬN CHUYỂN", "NGƯỜI NHẬN HÀNG")) {
                PdfPCell cell = cell(role + "\n(Ký, ghi rõ họ tên)", bold);
                cell.setBorder(Rectangle.NO_BORDER); cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                cell.setMinimumHeight(85); signatures.addCell(cell);
            }
            document.add(signatures); document.close(); return output.toByteArray();
        } catch (Exception e) { throw new IllegalStateException("Unable to generate delivery issue note PDF", e); }
    }

    private static String text(String s) { return s == null || s.isBlank() ? "—" : s; }
    private static String date(OffsetDateTime value) { return value == null ? "—" : DATE.format(value); }
    private static PdfPCell cell(String value, Font font) {
        PdfPCell cell = new PdfPCell(new Phrase(text(value), font));
        cell.setPadding(6); cell.setBorderColor(new Color(180, 190, 200)); return cell;
    }
}
