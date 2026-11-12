package com.aquasuite.ims.module.servicedesk.internal.controller;

import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobStatus;
import com.aquasuite.ims.module.servicedesk.internal.enums.TechnicalJobType;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.AssignTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CompleteTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.RejectTechnicalJobRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.TechnicalJobListItemResponse;
import com.aquasuite.ims.module.servicedesk.internal.service.TechnicalJobService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * API Công việc kỹ thuật (FE-13) - Giai đoạn 1, thao tác phía Technical Staff.
 * <ul>
 *   <li>UC-59: tra cứu danh sách/lịch ({@code GET /}) và xem chi tiết ({@code GET /{id}}).</li>
 *   <li>UC-60: tiếp nhận ({@code POST /{id}/accept}) hoặc từ chối ({@code POST /{id}/reject}).</li>
 *   <li>UC-61: ghi kết quả và hoàn tất ({@code POST /{id}/complete}).</li>
 * </ul>
 * Chủ cửa hàng được xem/thao tác toàn bộ; kỹ thuật viên chỉ trên công việc được phân cho mình (ràng buộc ở tầng service).
 */
@RestController
@RequestMapping("/api/v1/technical-jobs")
@RequiredArgsConstructor
public class TechnicalJobController {

    private final TechnicalJobService technicalJobService;

    /**
     * Tra cứu danh sách công việc kỹ thuật / lịch làm việc có phân trang và lọc (UC-59).
     * Lọc theo lịch hẹn bằng cặp {@code from}/{@code to} trên trường {@code scheduledAt}.
     */
    @GetMapping
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<Page<TechnicalJobListItemResponse>>> searchJobs(
            @RequestParam(required = false) TechnicalJobStatus status,
            @RequestParam(required = false) TechnicalJobType jobType,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
            @PageableDefault(size = 20, sort = "scheduledAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<TechnicalJobListItemResponse> page = technicalJobService.searchJobs(status, jobType, from, to, pageable);
        return ResponseEntity.ok(ApiResponse.ok(page));
    }

    /** Xem chi tiết một công việc kỹ thuật (UC-59). */
    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> getJobDetail(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.getJobDetail(id)));
    }

    /**
     * Tạo công việc kỹ thuật thủ công (thao tác của Chủ cửa hàng) — cho mọi khách và cả 3 loại công việc.
     * Không phụ thuộc loại đơn; có thể gán sẵn kỹ thuật viên hoặc để phân công sau.
     */
    @PostMapping
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> createJob(
            @Valid @RequestBody CreateTechnicalJobRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.createJob(request), "Đã tạo công việc kỹ thuật"));
    }

    /**
     * Phân công công việc cho một kỹ thuật viên (thao tác của Chủ cửa hàng).
     * Áp dụng cho công việc đang chờ tiếp nhận hoặc đã bị từ chối (phân công lại).
     */
    @PostMapping("/{id}/assign")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> assignJob(
            @PathVariable UUID id,
            @Valid @RequestBody AssignTechnicalJobRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.assignJob(id, request.technicianId(), request.scheduledAt()),
                "Đã phân công công việc kỹ thuật"));
    }

    /** Tiếp nhận công việc (UC-60): ASSIGNED → IN_PROGRESS. */
    @PostMapping("/{id}/accept")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> acceptJob(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.acceptJob(id), "Đã tiếp nhận công việc kỹ thuật"));
    }

    /** Từ chối công việc (UC-60): ASSIGNED → REJECTED. */
    @PostMapping("/{id}/reject")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> rejectJob(
            @PathVariable UUID id,
            @Valid @RequestBody RejectTechnicalJobRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.rejectJob(id, request.reason()),
                "Đã từ chối công việc kỹ thuật"));
    }

    /** Ghi kết quả và hoàn tất công việc (UC-61): IN_PROGRESS → COMPLETED. */
    @PostMapping("/{id}/complete")
    @PreAuthorize("hasAnyRole('SHOP_OWNER','TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<TechnicalJobDetailResponse>> completeJob(
            @PathVariable UUID id,
            @Valid @RequestBody CompleteTechnicalJobRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(technicalJobService.completeJob(id, request),
                "Đã ghi kết quả và hoàn tất công việc kỹ thuật"));
    }
}
