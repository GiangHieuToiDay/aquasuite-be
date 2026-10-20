package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.entity.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleAdminService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

/**
 * Điểm vào HTTP cho quản trị combo: danh sách, chi tiết, CRUD, trạng thái và tìm SKU.
 * Luồng: bind/validate request → kiểm query → BundleAdminService → bọc kết quả ApiResponse.
 * Controller không truy cập repository hoặc tính giá. JWT được kiểm qua bộ lọc dùng chung;
 * phần ghi Audit nghiệp vụ chưa tích hợp.
 */
@RestController
@RequestMapping("/api/v1/catalog/admin")
@RequiredArgsConstructor
public class BundleAdminController {
    private final BundleAdminService service;

    /**
     * Nhận bộ lọc/phân trang → service đọc combo chưa xóa → trả trang summary.
     */
    @GetMapping("/bundles")
    public ApiResponse<BundlePage<BundleCoreSummary>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus productStatus,
            @RequestParam(required = false) PublicationStatus publicationStatus,
            HttpServletRequest request) {
        validateQueryParameters(
                request, Set.of("page", "size", "keyword", "productStatus", "publicationStatus"));
        return ApiResponse.ok(service.list(page, size, keyword, productStatus, publicationStatus));
    }

    /**
     * Nhận UUID combo → service đọc và ghép chi tiết; combo không tồn tại/đã xóa trả 404.
     */
    @GetMapping("/bundles/{bundleId}")
    public ApiResponse<BundleCoreDetail> get(
            @PathVariable UUID bundleId, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.get(bundleId));
    }

    /**
     * Nhận dữ liệu tạo → service lưu aggregate nguyên tử → trả 201, Location và detail mới.
     */
    @PostMapping("/bundles")
    public ResponseEntity<ApiResponse<BundleCoreDetail>> create(
            @Valid @RequestBody BundleCoreCreateRequest payload, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        var detail = service.create(payload);
        return ResponseEntity.created(URI.create("/api/v1/catalog/admin/bundles/" + detail.bundleId()))
                .body(ApiResponse.ok(detail, "Đã tạo combo."));
    }

    /**
     * Nhận toàn bộ definition và expectedVersion → service kiểm xung đột rồi thay nội dung.
     */
    @PutMapping("/bundles/{bundleId}")
    public ApiResponse<BundleCoreDetail> update(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleCoreUpdateRequest payload,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.update(bundleId, payload));
    }

    /**
     * Nhận expectedVersion qua query → xóa mềm và trả trạng thái/version để client retry.
     */
    @DeleteMapping("/bundles/{bundleId}")
    public ApiResponse<BundleCoreMutationResult> delete(
            @PathVariable UUID bundleId,
            @RequestParam long expectedVersion,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of("expectedVersion"));
        return ApiResponse.ok(service.delete(bundleId, expectedVersion), "Đã xóa combo.");
    }

    /**
     * Nhận ACTIVE/INACTIVE và expectedVersion → đổi trạng thái nội bộ, không tự publish.
     */
    @PatchMapping("/bundles/{bundleId}/status")
    public ApiResponse<BundleCoreMutationResult> status(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleCoreStatusRequest payload,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.status(bundleId, payload));
    }

    /** Công bố sau khi service khóa cấu hình, kiểm version, giá SKU/option và lịch hiệu lực. */
    @PostMapping("/bundles/{bundleId}/publish")
    public ApiResponse<BundleCoreDetail> publish(@PathVariable UUID bundleId,
            @Valid @RequestBody BundleVersionRequest payload, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.publish(bundleId, payload), "MSG44");
    }

    /** Ngừng hiển thị combo trên API xem hàng sau khi transaction hoàn tất. */
    @PostMapping("/bundles/{bundleId}/unpublish")
    public ApiResponse<BundleCoreDetail> unpublish(@PathVariable UUID bundleId,
            @Valid @RequestBody BundleVersionRequest payload, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.unpublish(bundleId, payload));
    }

    /**
     * Nhận từ khóa/phân trang → service tìm SKU hợp lệ để chọn thành phần combo.
     */
    @GetMapping("/bundle-skus")
    public ApiResponse<BundlePage<BundleSkuResponse>> searchSkus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of("page", "size", "keyword"));
        return ApiResponse.ok(service.searchSkus(page, size, keyword));
    }

    /**
     * Chặn tên query ngoài danh sách cho phép hoặc một query bị gửi nhiều lần.
     * Ví dụ page=0&page=1 hoặc tham số viết sai trả 400 SYS_002 thay vì bị bỏ qua.
     */
    private void validateQueryParameters(HttpServletRequest request, Set<String> allowedParameters) {
        for (var parameter : request.getParameterMap().entrySet()) {
            boolean unknownParameter = !allowedParameters.contains(parameter.getKey());
            boolean repeatedParameter = parameter.getValue().length != 1;
            if (unknownParameter || repeatedParameter) {
                throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
            }
        }
    }
}
