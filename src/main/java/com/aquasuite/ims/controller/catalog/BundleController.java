package com.aquasuite.ims.controller.catalog;

import com.aquasuite.ims.dto.catalog.request.BundleEvaluationRequest;
import com.aquasuite.ims.dto.catalog.request.BundlePageRequest;
import com.aquasuite.ims.dto.catalog.response.BundleEvaluationResponse;
import com.aquasuite.ims.dto.catalog.response.BundleResponse;
import com.aquasuite.ims.service.catalog.helper.BundleEvaluationService;
import com.aquasuite.ims.service.catalog.BundleQueryService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.exception.*;

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
