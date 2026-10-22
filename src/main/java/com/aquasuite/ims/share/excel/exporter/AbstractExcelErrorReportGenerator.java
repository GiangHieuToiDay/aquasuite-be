package com.aquasuite.ims.share.excel.exporter;

import com.aquasuite.ims.share.excel.dto.ExcelRowError;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

@Component
public class AbstractExcelErrorReportGenerator {

    public byte[] generateErrorWorkbook(String reportTitle, List<ExcelRowError> errors)
            throws IOException {
        try (SXSSFWorkbook workbook = new SXSSFWorkbook(100);
                ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            Sheet sheet = workbook.createSheet("Error_Report");

            // Style cho Header
            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setColor(IndexedColors.WHITE.getIndex());
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.DARK_RED.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Style cho dòng lỗi (Nền hồng nhạt)
            CellStyle errorRowStyle = workbook.createCellStyle();
            errorRowStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            errorRowStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Tạo Header
            Row header = sheet.createRow(0);
            String[] cols = {
                "Dòng trên Excel (Row)",
                "Mã định danh (SKU / Code)",
                "Chi tiết lỗi cần sửa (Error Message)"
            };
            for (int i = 0; i < cols.length; i++) {
                Cell cell = header.createCell(i);
                cell.setCellValue(cols[i]);
                cell.setCellStyle(headerStyle);
                sheet.setColumnWidth(i, (i == 2) ? 15000 : 6000);
            }

            // Đổ danh sách lỗi
            int rowIdx = 1;
            for (ExcelRowError err : errors) {
                Row row = sheet.createRow(rowIdx++);
                Cell c0 = row.createCell(0);
                c0.setCellValue("Dòng " + err.rowNumber());
                c0.setCellStyle(errorRowStyle);

                Cell c1 = row.createCell(1);
                c1.setCellValue(err.rowKey());
                c1.setCellStyle(errorRowStyle);

                Cell c2 = row.createCell(2);
                c2.setCellValue(err.errorMessage());
                c2.setCellStyle(errorRowStyle);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
