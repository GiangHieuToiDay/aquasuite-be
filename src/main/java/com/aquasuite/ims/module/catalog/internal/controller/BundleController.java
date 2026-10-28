package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.req.BundleEvaluationRequest;
import com.aquasuite.ims.module.catalog.internal.payload.req.BundlePageRequest;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleEvaluationResponse;
import com.aquasuite.ims.module.catalog.internal.payload.res.BundleResponse;
import com.aquasuite.ims.module.catalog.internal.service.BundleEvaluationService;
import com.aquasuite.ims.module.catalog.internal.service.BundleQueryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * API xem combo và xem trước giá/tồn, vẫn yêu cầu JWT qua bộ lọc dùng chung. Chưa tích hợp media;
 * preview không sửa giỏ hàng hoặc giữ tồn.
 */
@RestController
@RequestMapping("/api/v1/catalog/bundle")
@RequiredArgsConstructor
public class BundleController {
    private final BundleQueryService query;
    private final BundleEvaluationService evaluation;

    @GetMapping
    public ApiResponse<PageResponse<BundleResponse>> list(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest request) {
        validateQuery(request, Set.of("page", "size", "keyword"));
        return ApiResponse.ok(
                query.getPublishedBundles(new BundlePageRequest(page, size, keyword, null, null)));
    }

    @GetMapping("/{bundleId}")
    public ApiResponse<BundleResponse> get(
            @PathVariable UUID bundleId, HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(query.getPublishedBundleDetails(bundleId));
    }

    @PostMapping("/{bundleId}/evaluations")
    public ApiResponse<BundleEvaluationResponse> evaluate(
            @PathVariable UUID bundleId,
            @Valid @RequestBody BundleEvaluationRequest payload,
            HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(evaluation.checkAvailability(bundleId, payload));
    }

    /** Từ chối tham số lạ hoặc lặp để lỗi nhập liệu không bị âm thầm bỏ qua. */
    private void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().entrySet().stream()
                .anyMatch(p -> !allowed.contains(p.getKey()) || p.getValue().length != 1))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }
}
