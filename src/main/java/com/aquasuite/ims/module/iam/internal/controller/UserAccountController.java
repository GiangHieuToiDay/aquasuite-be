package com.aquasuite.ims.module.iam.internal.controller;

import com.aquasuite.ims.module.iam.api.IamApi;

import com.aquasuite.ims.module.iam.internal.payload.request.ChangeUserStatusRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.CreateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdateUserAccountRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountPageResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.AccountStatsResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.UserAccountResponse;
import com.aquasuite.ims.module.iam.internal.service.UserAccountService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SHOP_OWNER')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountController {

    UserAccountService service;
    IamApi iamApi;

    @GetMapping("/shippers")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<AccountPageResponse> shippers(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 100)) {
            throw new com.aquasuite.ims.share.exception.AppException(
                    com.aquasuite.ims.share.exception.CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("username").and(org.springframework.data.domain.Sort.by("id")));
        return ApiResponse.ok(service.search(keyword, UUID.fromString("10000000-0000-4000-8000-000000000005"),
                "ACTIVE", pageable, iamApi.getCurrentUserId()));
    }

    @GetMapping("/stats")
    public ApiResponse<AccountStatsResponse> stats() { return ApiResponse.ok(service.stats()); }
    @GetMapping
    public ApiResponse<AccountPageResponse> search(@RequestParam(required = false) String keyword,
                                                   @RequestParam(required = false) UUID roleId,
                                                   @RequestParam(required = false) String status, Pageable pageable) {
        return ApiResponse.ok(service.search(keyword, roleId, status, pageable, iamApi.getCurrentUserId()));
    }
    @GetMapping("/{id}")
    public ApiResponse<UserAccountResponse> detail(@PathVariable UUID id) { return ApiResponse.ok(service.detail(id, iamApi.getCurrentUserId())); }
    @PostMapping
    public ResponseEntity<ApiResponse<UserAccountResponse>> create(@Valid @RequestBody CreateUserAccountRequest request) {
        return ResponseEntity.status(201).body(ApiResponse.ok(service.create(request, iamApi.getCurrentUserId()), "Staff account created successfully and credentials emailed."));
    }
    @PatchMapping("/{id}")
    public ApiResponse<UserAccountResponse> update(@PathVariable UUID id, @Valid @RequestBody UpdateUserAccountRequest request) {
        return ApiResponse.ok(service.update(id, request, iamApi.getCurrentUserId()));
    }
    @PatchMapping("/{id}/status")
    public ApiResponse<UserAccountResponse> changeStatus(@PathVariable UUID id, @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request, iamApi.getCurrentUserId()));
    }
}
