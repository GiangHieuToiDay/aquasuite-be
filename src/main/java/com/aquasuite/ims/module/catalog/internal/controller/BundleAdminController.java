package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.entity.BundleConfiguration.PublicationStatus;
import com.aquasuite.ims.module.catalog.internal.enums.ProductStatus;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundlePageRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.ChangeBundleStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.ChangePublicationStatusRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.CreateBundleRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.UpdateBundleRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleSkuResponse;
import com.aquasuite.ims.module.catalog.internal.service.BundleAdminService;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

/**
 * Điểm vào HTTP cho quản trị combo: danh sách, chi tiết, CRUD, trạng thái và tìm SKU. Luồng:
 * bind/validate request → kiểm query → BundleAdminService → bọc kết quả ApiResponse. Controller
 * không truy cập repository hoặc tính giá. JWT được kiểm qua bộ lọc dùng chung; phần ghi Audit
 * nghiệp vụ chưa tích hợp.
 */
@PreAuthorize("hasRole('SHOP_OWNER')")
@RestController
@RequestMapping("/api/v1/catalog/bundle/admin")
@RequiredArgsConstructor
public class BundleAdminController {
    private final BundleAdminService service;
    private final BundleQueryService query;

    /** Nhận bộ lọc/phân trang → service đọc combo chưa xóa → trả trang response đầy đủ. */
    @GetMapping
    public ApiResponse<PageResponse<BundleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) ProductStatus productStatus,
            @RequestParam(required = false) PublicationStatus publicationStatus,
            HttpServletRequest request) {
        validateQueryParameters(
                request, Set.of("page", "size", "keyword", "productStatus", "publicationStatus"));
        return ApiResponse.ok(
                query.searchBundles(
                        new BundlePageRequest(
                                page, size, keyword, productStatus, publicationStatus)));
    }

    /** Nhận UUID combo → service đọc và ghép chi tiết; combo không tồn tại/đã xóa trả 404. */
    @GetMapping("/{bundleId}")
    public ApiResponse<BundleResponse> get(
            @PathVariable UUID bundleId, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(query.getBundleDetails(bundleId));
    }

    /** Nhận dữ liệu tạo → service lưu aggregate nguyên tử → trả 201, Location và detail mới. */
    @PostMapping
    public ResponseEntity<ApiResponse<BundleResponse>> create(
            @Valid @RequestBody CreateBundleRequest payload, HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        var detail = service.createBundle(payload);
        return ResponseEntity.created(
                        URI.create("/api/v1/catalog/bundle/admin/" + detail.bundleId()))
                .body(ApiResponse.ok(detail, "Đã tạo combo."));
    }

    /** Nhận toàn bộ definition và expectedVersion → service kiểm xung đột rồi thay nội dung. */
    @PutMapping("/{bundleId}")
    public ApiResponse<BundleResponse> update(
            @PathVariable UUID bundleId,
            @Valid @RequestBody UpdateBundleRequest payload,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.updateBundle(bundleId, payload));
    }

    /** Nhận expectedVersion qua query → xóa mềm và trả thông báo thành công, không có data. */
    @DeleteMapping("/{bundleId}")
    public ApiResponse<Void> delete(
            @PathVariable UUID bundleId,
            @RequestParam long expectedVersion,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of("expectedVersion"));
        service.deleteBundle(bundleId, expectedVersion);
        return ApiResponse.ok(null, "Đã xóa combo.");
    }

    /** Nhận ACTIVE/INACTIVE và expectedVersion → đổi trạng thái nội bộ, không tự publish. */
    @PatchMapping("/{bundleId}/status")
    public ApiResponse<BundleResponse> status(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangeBundleStatusRequest payload,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.changeProductStatus(bundleId, payload));
    }

    /** One publication endpoint; service validates version before transition or no-op. */
    @PatchMapping("/{bundleId}/publication-status")
    public ApiResponse<BundleResponse> publication(
            @PathVariable UUID bundleId,
            @Valid @RequestBody ChangePublicationStatusRequest payload,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of());
        return ApiResponse.ok(service.changePublicationStatus(bundleId, payload));
    }

    /** Retired routes return an explicit 404 instead of reaching the shared fallback handler. */
    @PostMapping({"/{bundleId}/publish", "/{bundleId}/unpublish"})
    public ApiResponse<Void> retiredPublicationRoute(@PathVariable UUID bundleId) {
        throw new AppException(CommonErrorCode.RESOURCE_NOT_FOUND);
    }

    /** Nhận từ khóa/phân trang → service tìm SKU hợp lệ để chọn thành phần combo. */
    @GetMapping("/skus")
    public ApiResponse<PageResponse<BundleSkuResponse>> searchSkus(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        validateQueryParameters(request, Set.of("page", "size", "keyword"));
        return ApiResponse.ok(query.searchSkus(page, size, keyword));
    }

    /**
     * Chặn tên query ngoài danh sách cho phép hoặc một query bị gửi nhiều lần. Ví dụ page=0&page=1
     * hoặc tham số viết sai trả 400 SYS_002 thay vì bị bỏ qua.
     */
    private void validateQueryParameters(
            HttpServletRequest request, Set<String> allowedParameters) {
        for (var parameter : request.getParameterMap().entrySet()) {
            boolean unknownParameter = !allowedParameters.contains(parameter.getKey());
            boolean repeatedParameter = parameter.getValue().length != 1;
            if (unknownParameter || repeatedParameter) {
                throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
            }
        }
    }
}
