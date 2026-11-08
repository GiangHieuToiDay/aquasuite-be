package com.aquasuite.ims.module.finance.internal.pdf;

import com.aquasuite.ims.module.finance.internal.payload.response.CustomerDebtDetailResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.InvoiceDebtResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentReceiptResponse;
import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Paragraph;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;

@Service
public class StatementPdfService {
    public StatementDocument generate(CustomerDebtDetailResponse detail) {
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Document document = new Document();
            PdfWriter.getInstance(document, output);
            document.open();
            document.add(new Paragraph("AquaSuite - Customer Statement of Account"));
            document.add(new Paragraph(detail.customer().customerCode() + " - " + detail.customer().customerName()));
            document.add(new Paragraph("Opening balance: 0.00"));
            document.add(new Paragraph("Outstanding: " + detail.summary().outstandingBalance()));
            document.add(new Paragraph("Overdue: " + detail.summary().overdueBalance()));
            document.add(new Paragraph("Available credit: " + detail.summary().availableCredit()));
            document.add(new Paragraph("Issued-invoice debit movements"));
            PdfPTable table = new PdfPTable(5);
            table.addCell("Invoice"); table.addCell("Issued"); table.addCell("Due"); table.addCell("Paid"); table.addCell("Outstanding");
            for (InvoiceDebtResponse invoice : detail.invoices()) {
                table.addCell(invoice.invoiceCode());
                table.addCell(String.valueOf(invoice.issueDate()));
                table.addCell(String.valueOf(invoice.dueDate()));
                table.addCell(format(invoice.verifiedPaidAmount()));
                table.addCell(format(invoice.outstandingAmount()));
            }
            document.add(table);
            document.add(new Paragraph("Verified-payment credit movements"));
            PdfPTable receipts = new PdfPTable(5);
            receipts.addCell("Receipt"); receipts.addCell("Date"); receipts.addCell("Method"); receipts.addCell("Amount"); receipts.addCell("Invoice");
            for (PaymentReceiptResponse receipt : detail.verifiedReceipts()) {
                receipts.addCell(receipt.transactionCode());
                receipts.addCell(String.valueOf(receipt.transactionDate()));
                receipts.addCell(String.valueOf(receipt.paymentMethod()));
                receipts.addCell(format(receipt.amount()));
                receipts.addCell(String.valueOf(receipt.invoiceId()));
            }
            document.add(receipts);
            document.add(new Paragraph("Unallocated verified order receipts: " + format(detail.unallocatedVerifiedReceiptAmount())));
            document.add(new Paragraph("Closing balance: " + format(detail.summary().outstandingBalance())));
            document.close();
            return new StatementDocument("statement-" + detail.customer().customerCode() + ".pdf", output.toByteArray());
        } catch (DocumentException exception) {
            throw new IllegalStateException("Unable to generate statement PDF", exception);
        } catch (java.io.IOException exception) {
            throw new IllegalStateException("Unable to close statement PDF output", exception);
        }
    }

    private String format(BigDecimal value) {
        return value == null ? BigDecimal.ZERO.toPlainString() : value.toPlainString();
    }
}
