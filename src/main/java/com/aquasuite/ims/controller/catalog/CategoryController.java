package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.dto.catalog.excel.CategoryImportJob;
import com.aquasuite.ims.dto.catalog.request.CreateCategoryRequest;
import com.aquasuite.ims.dto.catalog.request.UpdateCategoryRequest;
import com.aquasuite.ims.dto.catalog.response.CategoryBriefResponse;
import com.aquasuite.ims.dto.catalog.response.CategoryResponse;
import com.aquasuite.ims.service.catalog.CategoryService;
import com.aquasuite.ims.service.catalog.excel.CategoryImportService;
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
@RequestMapping("/api/v1/catalog/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final MediaType XLSX = MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final CategoryService categoryService;
    private final CategoryImportService categoryImportService;

    // ── CRUD Category ────────────────────────────────────────────────────────

    /** Trả về danh sách danh mục hoạt động dùng cho bộ lọc và dropdown. */
    @GetMapping("/options")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<List<CategoryBriefResponse>> getActiveCategoryOptions() {
        return ApiResponse.ok(categoryService.getActiveCategoryOptions());
    }

    /** Tìm kiếm và phân trang danh mục theo trạng thái. */
    @GetMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<PageResponse<CategoryResponse>> listCategories(
            @Valid @ModelAttribute PageQuery pageQuery,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status
    ) {
        return ApiResponse.ok(categoryService.listCategories(pageQuery, search, status));
    }

    /** Trả về chi tiết một danh mục theo ID. */
    @GetMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_READ')")
    public ApiResponse<CategoryResponse> getCategoryDetail(@PathVariable UUID categoryId) {
        return ApiResponse.ok(categoryService.getCategoryDetail(categoryId));
    }

    /** Tạo danh mục mới và kiểm tra quan hệ cha nếu được cung cấp. */
    @PostMapping
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<CategoryResponse>> createCategory(
            @Valid @RequestBody CreateCategoryRequest request
    ) {
        CategoryResponse created = categoryService.createCategory(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(created));
    }

    /** Cập nhật danh mục và ngăn tạo chu trình phân cấp. */
    @PutMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_UPDATE')")
    public ApiResponse<CategoryResponse> updateCategory(
            @PathVariable UUID categoryId,
            @Valid @RequestBody UpdateCategoryRequest request
    ) {
        return ApiResponse.ok(categoryService.updateCategory(categoryId, request));
    }

    /** Xóa mềm danh mục nếu không còn danh mục con hoặc sản phẩm tham chiếu. */
    @DeleteMapping("/{categoryId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_DELETE')")
    public ApiResponse<Void> deleteCategory(@PathVariable UUID categoryId) {
        categoryService.deleteCategory(categoryId);
        return ApiResponse.ok(null);
    }

    // ── Excel Import (from CategoryImportController) ─────────────────────────

    @GetMapping("/import-template")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<byte[]> template() {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Category_Import_Template.xlsx")
                .contentType(XLSX).body(categoryImportService.template());
    }

    @PostMapping(value = "/import-jobs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<ApiResponse<Map<String, Object>>> submit(@RequestParam("file") MultipartFile file) {
        CategoryImportJob job = categoryImportService.submit(file);
        return ResponseEntity.accepted().body(ApiResponse.ok(Map.of("jobId", job.jobId, "status", job.status)));
    }

    @GetMapping("/import-jobs/{jobId}")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ApiResponse<Map<String, Object>> progress(@PathVariable UUID jobId) {
        CategoryImportJob job = categoryImportService.get(jobId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("jobId", job.jobId); data.put("status", job.status); data.put("totalRows", job.totalRows);
        data.put("processedRows", job.processedRows); data.put("createdRows", job.createdRows);
        data.put("skippedRows", job.skippedRows); data.put("errorRows", job.errorRows);
        data.put("percentage", job.percentage); data.put("message", job.message);
        data.put("startedAt", job.startedAt); data.put("finishedAt", job.finishedAt);
        return ApiResponse.ok(data);
    }

    @GetMapping("/import-jobs/{jobId}/error-report")
    @PreAuthorize("hasAuthority('CATALOG_CATEGORY_CREATE')")
    public ResponseEntity<byte[]> report(@PathVariable UUID jobId) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=Category_Import_Report.xlsx")
                .contentType(XLSX).body(categoryImportService.report(jobId));
    }
}
