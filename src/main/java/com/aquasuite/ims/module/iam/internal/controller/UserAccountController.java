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
@PreAuthorize("hasRole('STORE_OWNER')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class UserAccountController {

    UserAccountService service;
    IamApi iamApi;

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
