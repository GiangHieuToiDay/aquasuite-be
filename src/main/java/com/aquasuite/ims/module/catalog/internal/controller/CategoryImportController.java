package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.service.excel.CategoryImportJob;
import com.aquasuite.ims.module.catalog.internal.service.excel.CategoryImportService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
public class CategoryImportController {
    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
    private final CategoryImportService service;

    @GetMapping("/import-template")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Category_Import_Template.xlsx")
                .contentType(XLSX).body(service.template());
    }

    @PostMapping(value = "/import-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@RequestParam("file") MultipartFile file) {
        CategoryImportJob job = service.submit(file);
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of("jobId", job.jobId, "status", job.status)));
    }

    @GetMapping("/import-jobs/{jobId}")
    public ApiResponse<Map<String, Object>> progress(@PathVariable UUID jobId) {
        CategoryImportJob job = service.get(jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.jobId); data.put("status", job.status); data.put("totalRows", job.totalRows);
        data.put("processedRows", job.processedRows); data.put("createdRows", job.createdRows);
        data.put("skippedRows", job.skippedRows); data.put("errorRows", job.errorRows);
        data.put("percentage", job.percentage); data.put("message", job.message);
        data.put("startedAt", job.startedAt); data.put("finishedAt", job.finishedAt);
        return ApiResponse.ok(data);
    }

    @GetMapping("/import-jobs/{jobId}/error-report")
    public ResponseEntity<byte[]> report(@PathVariable UUID jobId) {
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Category_Import_Report.xlsx")
                .contentType(XLSX).body(service.report(jobId));
    }
}
