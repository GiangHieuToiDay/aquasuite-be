package com.aquasuite.ims.module.purchasing.internal.service;

import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderItemResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Font;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.text.DecimalFormat;

@Service
public class PurchaseOrderPdfService {

    private static final DecimalFormat MONEY_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat QTY_FORMAT = new DecimalFormat("#,##0.##");

    public byte[] generatePdf(PurchaseOrderResponse po) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();

            boolean isDraft = po.getStatus() == PurchaseOrderStatus.DRAFT;

            // Title
            Font titleFont = new Font(Font.HELVETICA, 16, Font.BOLD);
            Paragraph title = new Paragraph("AQUASUITE - DON MUA HANG (PURCHASE ORDER)", titleFont);
            title.setAlignment(Paragraph.ALIGN_CENTER);
            document.add(title);

            if (isDraft) {
                Font draftFont = new Font(Font.HELVETICA, 12, Font.BOLD);
                Paragraph draftNotice = new Paragraph("*** BAN NHAP (DRAFT) - DUNG XIN BAO GIA TU NHA CUNG CAP ***", draftFont);
                draftNotice.setAlignment(Paragraph.ALIGN_CENTER);
                draftNotice.setSpacingAfter(10);
                document.add(draftNotice);
            }

            document.add(new Paragraph("Ma PO: " + po.getPoCode()));
            document.add(new Paragraph("Ngay lap: " + (po.getOrderDate() != null ? po.getOrderDate() : "")));
            document.add(new Paragraph("Ngay giao du kien: " + (po.getExpectedDate() != null ? po.getExpectedDate() : "Chua xac dinh")));
            document.add(new Paragraph("Kho nhan: " + (po.getWarehouseCode() != null ? po.getWarehouseCode() : "WH_MAIN")));
            document.add(new Paragraph("Nha cung cap: " + (po.getSupplierName() != null ? po.getSupplierName() : "") + " (" + po.getSupplierCode() + ")"));
            if (po.getSupplierPhone() != null && !po.getSupplierPhone().isBlank()) {
                document.add(new Paragraph("Dien thoai NCC: " + po.getSupplierPhone()));
            }
            if (po.getNote() != null && !po.getNote().isBlank()) {
                document.add(new Paragraph("Ghi chu / Thoa thuan: " + po.getNote()));
            }

            Paragraph spacer = new Paragraph(" ");
            spacer.setSpacingAfter(8);
            document.add(spacer);

            if (isDraft) {
                // Draft: only items and quantities, no costs
                PdfPTable table = new PdfPTable(5);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1, 3, 5, 2, 2});

                table.addCell(new Phrase("STT"));
                table.addCell(new Phrase("SKU"));
                table.addCell(new Phrase("Ten san pham"));
                table.addCell(new Phrase("Don vi"));
                table.addCell(new Phrase("So luong dat"));

                int idx = 1;
                if (po.getItems() != null) {
                    for (PurchaseOrderItemResponse item : po.getItems()) {
                        table.addCell(String.valueOf(idx++));
                        table.addCell(item.getSku() != null ? item.getSku() : "");
                        table.addCell(item.getProductName() != null ? item.getProductName() : "");
                        table.addCell(item.getUnit() != null ? item.getUnit() : "");
                        table.addCell(formatQty(item.getQuantity()));
                    }
                }
                document.add(table);
            } else {
                // Confirmed or higher: full table with costs & totals
                PdfPTable table = new PdfPTable(7);
                table.setWidthPercentage(100);
                table.setWidths(new float[]{1, 2.5f, 4, 1.5f, 2, 2.5f, 2.5f});

                table.addCell(new Phrase("STT"));
                table.addCell(new Phrase("SKU"));
                table.addCell(new Phrase("Ten san pham"));
                table.addCell(new Phrase("Don vi"));
                table.addCell(new Phrase("So luong dat"));
                table.addCell(new Phrase("Don gia"));
                table.addCell(new Phrase("Thanh tien"));

                int idx = 1;
                if (po.getItems() != null) {
                    for (PurchaseOrderItemResponse item : po.getItems()) {
                        table.addCell(String.valueOf(idx++));
                        table.addCell(item.getSku() != null ? item.getSku() : "");
                        table.addCell(item.getProductName() != null ? item.getProductName() : "");
                        table.addCell(item.getUnit() != null ? item.getUnit() : "");
                        table.addCell(formatQty(item.getQuantity()));
                        table.addCell(formatMoney(item.getUnitCost()));
                        table.addCell(formatMoney(item.getLineTotal()));
                    }
                }
                document.add(table);

                Paragraph totalPara = new Paragraph("Tong gia tri don mua: " + formatMoney(po.getTotalAmount()) + " VND", titleFont);
                totalPara.setAlignment(Paragraph.ALIGN_RIGHT);
                totalPara.setSpacingBefore(10);
                document.add(totalPara);
            }

            Paragraph signSpacer = new Paragraph(" ");
            signSpacer.setSpacingBefore(20);
            document.add(signSpacer);

            PdfPTable signTable = new PdfPTable(2);
            signTable.setWidthPercentage(100);
            PdfPCell c1 = new PdfPCell(new Phrase("NGUOI LAP DON\n(Ky va ghi ro ho ten)"));
            c1.setBorder(0);
            c1.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            PdfPCell c2 = new PdfPCell(new Phrase("DAI DIEN NHA CUNG CAP\n(Ky va ghi ro ho ten)"));
            c2.setBorder(0);
            c2.setHorizontalAlignment(PdfPCell.ALIGN_CENTER);
            signTable.addCell(c1);
            signTable.addCell(c2);
            document.add(signTable);

            document.close();
            return output.toByteArray();
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate PO PDF", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to close PO PDF output", exception);
        }
    }

    private String formatMoney(BigDecimal amount) {
        if (amount == null) return "0.00";
        return MONEY_FORMAT.format(amount);
    }

    private String formatQty(BigDecimal qty) {
        if (qty == null) return "0";
        return QTY_FORMAT.format(qty);
    }
}