package com.aquasuite.ims.module.catalog.internal.controller;

import com.aquasuite.ims.module.catalog.internal.payload.*;
import com.aquasuite.ims.module.catalog.internal.service.BundleSkuQueryService;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.exception.*;

import jakarta.servlet.http.HttpServletRequest;

import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/v1/catalog/admin/bundle-skus")
@RequiredArgsConstructor
public class BundleSkuController {
    private final BundleSkuQueryService service;

    @GetMapping
    public ApiResponse<BundlePage<BundleSkuResponse>> search(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            HttpServletRequest r) {
        if (!Set.of("page", "size", "keyword").containsAll(r.getParameterMap().keySet())
                || r.getParameterMap().values().stream().anyMatch(v -> v.length != 1))
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        return ApiResponse.ok(service.search(page, size, keyword));
    }
}
