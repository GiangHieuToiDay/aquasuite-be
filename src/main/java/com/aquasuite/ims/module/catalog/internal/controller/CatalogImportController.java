package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.excel.ImportMode;
import com.aquasuite.ims.module.catalog.internal.service.excel.ProductBulkExcelImporter;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.excel.dto.ImportProgressDto;
import com.aquasuite.ims.share.excel.exporter.AbstractExcelErrorReportGenerator;
import com.aquasuite.ims.share.excel.storage.TempExcelFileManager;
import com.aquasuite.ims.share.excel.tracker.RedisImportProgressTracker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/api/v1/catalog/import")
@RequiredArgsConstructor
public class CatalogImportController {

    private final TempExcelFileManager tempFileManager;
    private final RedisImportProgressTracker progressTracker;
    private final AbstractExcelErrorReportGenerator errorReportGenerator;
    private final ProductBulkExcelImporter productBulkExcelImporter;

    /**
     * API 1: Bắt đầu Import file Excel
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> uploadAndImport(
            @RequestParam("file") MultipartFile file,
            @RequestParam(name = "importMode", defaultValue = "SKIP_EXISTING") ImportMode importMode
    ) {
        File tempFile = tempFileManager.saveToTempFile(file, "catalog");
        String jobId = "imp_" + UUID.randomUUID();

        log.info("Received catalog import request. JobId: {}, ImportMode: {}, FileName: {}",
                jobId, importMode, file.getOriginalFilename());

        productBulkExcelImporter.startAsyncImport(tempFile, jobId, importMode);

        Map<String, Object> result = Map.of(
                "jobId", jobId,
                "importMode", importMode.name(),
                "fileName", file.getOriginalFilename() != null ? file.getOriginalFilename() : "catalog.xlsx"
        );

        return ResponseEntity.accepted()
                .body(ApiResponse.ok(result, "File import đã được tiếp nhận và đang tiến hành xử lý ngầm"));
    }

    /**
     * API 2: Lấy tiến độ xử lý (%) và số dòng thành công/thất bại
     */
    @GetMapping("/{jobId}/progress")
    public ResponseEntity<ApiResponse<ImportProgressDto>> getProgress(@PathVariable("jobId") String jobId) {
        ImportProgressDto progress = progressTracker.getProgress(jobId);
        return ResponseEntity.ok(ApiResponse.ok(progress, "Tiến độ import sản phẩm"));
    }

    /**
     * API 3: Tải file Excel báo cáo các dòng lỗi (nếu có lỗi)
     */
    @GetMapping("/{jobId}/error-report")
    public ResponseEntity<byte[]> downloadErrorReport(@PathVariable("jobId") String jobId) throws IOException {
        ImportProgressDto progress = progressTracker.getProgress(jobId);
        byte[] excelBytes = errorReportGenerator.generateErrorWorkbook("Catalog_Import_Errors", progress.getErrors());

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Catalog_Import_Errors_" + jobId + ".xlsx")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excelBytes);
    }
}
