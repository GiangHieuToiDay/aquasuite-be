package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.dto.catalog.excel.BrandImportJob;
import com.aquasuite.ims.dto.catalog.request.CreateBrandRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateBrandRequest;
import com.aquasuite.ims.dto.catalog.response.BrandBriefResponse;
import com.aquasuite.ims.dto.catalog.response.BrandResponse;
import com.aquasuite.ims.service.catalog.BrandService;
import com.aquasuite.ims.service.catalog.excel.BrandImportService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageQuery;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/catalog/brands")
@RequiredArgsConstructor
public class BrandController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final BrandService brandService;
    private final BrandImportService brandImportService;

    // ── CRUD Brand ───────────────────────────────────────────────────────────

    /** Trả về danh sách thương hiệu hoạt động dùng cho bộ lọc và dropdown. */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<List<BrandBriefResponse>> getActiveBrandOptions() {
        return ApiResponse.ok(brandService.getActiveBrandOptions());
    }

    /** Tìm kiếm và phân trang thương hiệu theo trạng thái. */
    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<PageResponse<BrandResponse>> listBrands(
            @Valid @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(brandService.listBrands(pageQuery, search, status));
    }

    /** Trả về chi tiết một thương hiệu theo ID. */
    @GetMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_READ')")
    public ApiResponse<BrandResponse> getBrandDetail(@PathVariable UUID brandId) {
        return ApiResponse.ok(brandService.getBrandDetail(brandId));
    }

    /** Tạo thương hiệu mới khi người dùng có quyền tương ứng. */
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<ApiResponse<BrandResponse>> createBrand(
            @Valid @RequestBody CreateBrandRequest request
    ) {
        BrandResponse created = brandService.createBrand(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** Thay đổi thông tin thương hiệu hiện có. */
    @PutMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_UPDATE')")
    public ApiResponse<BrandResponse> updateBrand(
            @PathVariable UUID brandId,
            @Valid @RequestBody UpdateBrandRequest request
    ) {
        return ApiResponse.ok(brandService.updateBrand(brandId, request));
    }

    /** Xóa mềm thương hiệu nếu không còn sản phẩm tham chiếu. */
    @DeleteMapping("/{brandId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_DELETE')")
    public ApiResponse<Void> deleteBrand(@PathVariable UUID brandId) {
        brandService.deleteBrand(brandId);
        return ApiResponse.ok(null);
    }

    // ── Excel Import (from BrandImportController, path standardized to /api/v1/catalog/brands) ─

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Brand_Import_Template.xlsx")
                .contentType(XLSX).body(brandImportService.template());
    }

    @PostMapping(value = "/import-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@RequestParam("file") MultipartFile file) {
        BrandImportJob job = brandImportService.submit(file);
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of("jobId", job.jobId, "status", "QUEUED")));
    }

    @GetMapping("/import-jobs/{jobId}")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ApiResponse<Map<String, Object>> progress(@PathVariable UUID jobId) {
        BrandImportJob job = brandImportService.get(jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.jobId); data.put("status", job.status); data.put("totalRows", job.totalRows);
        data.put("processedRows", job.processedRows); data.put("createdRows", job.createdRows);
        data.put("skippedRows", job.skippedRows); data.put("errorRows", job.errorRows);
        data.put("percentage", job.percentage); data.put("message", job.message);
        data.put("startedAt", job.startedAt); data.put("finishedAt", job.finishedAt);
        return ApiResponse.ok(data);
    }

    @GetMapping("/import-jobs/{jobId}/error-report")
    @PreAuthorize("hasAuthority('CATALOG_BRAND_CREATE')")
    public ResponseEntity<byte[]> report(@PathVariable UUID jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Brand_Import_Report.xlsx")
                .contentType(XLSX).body(brandImportService.report(jobId));
    }
}
