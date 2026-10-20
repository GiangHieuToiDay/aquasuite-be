package com.aquasuite.ims.module.catalog.internal.controller;
import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleStorefrontService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;

/** Storefront browsing/preview behind the shared JWT filter; no cart mutation or reservation. Media is deferred. */
@RestController
@RequestMapping("/api/v1/catalog/bundles")
@RequiredArgsConstructor
public class BundleStorefrontController {
    private final BundleStorefrontService service;

    @GetMapping
    public ApiResponse<BundlePage<BundleCoreSummary>> list(@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size, @RequestParam(required=false) String keyword,
            HttpServletRequest request) {
        validateQuery(request, Set.of("page", "size", "keyword"));
        return ApiResponse.ok(service.list(page, size, keyword));
    }

    @GetMapping("/{bundleId}")
    public ApiResponse<BundleCoreDetail> get(@PathVariable UUID bundleId, HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(service.get(bundleId));
    }

    @PostMapping("/{bundleId}/evaluations")
    public ApiResponse<BundleEvaluation> evaluate(@PathVariable UUID bundleId,
            @Valid @RequestBody BundleEvaluationRequest payload, HttpServletRequest request) {
        validateQuery(request, Set.of());
        return ApiResponse.ok(service.evaluate(bundleId, payload));
    }

    private void validateQuery(HttpServletRequest request, Set<String> allowed) {
        if (request.getParameterMap().entrySet().stream()
                .anyMatch(p -> !allowed.contains(p.getKey()) || p.getValue().length != 1))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
    }
}
