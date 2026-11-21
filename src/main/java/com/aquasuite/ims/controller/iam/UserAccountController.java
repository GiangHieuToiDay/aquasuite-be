package com.aquasuite.ims.controller.iam;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.request.ChangeUserStatusRequest;
import com.aquasuite.ims.dto.iam.request.CreateUserAccountRequest;
import com.aquasuite.ims.dto.iam.request.UpdateUserAccountRequest;
import com.aquasuite.ims.dto.iam.response.AccountPageResponse;
import com.aquasuite.ims.dto.iam.response.AccountStatsResponse;
import com.aquasuite.ims.dto.iam.response.UserAccountResponse;
import com.aquasuite.ims.service.iam.UserAccountService;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/iam/users")
@PreAuthorize("hasRole('SHOP_OWNER')")
public class UserAccountController {

    private final UserAccountService service;
    private final IamQueryService iamApi;

    @Autowired
    public UserAccountController(UserAccountService service, IamQueryService iamApi) {
        this.service = service;
        this.iamApi = iamApi;
    }

    @GetMapping("/shippers")
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
    public ApiResponse<AccountPageResponse> shippers(@RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        if (page < 0 || size < 1 || size > 100 || (keyword != null && keyword.length() > 100)) {
            throw new com.aquasuite.ims.exception.AppException(
                    com.aquasuite.ims.exception.CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        var pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by("username").and(org.springframework.data.domain.Sort.by("id")));
        return ApiResponse.ok(service.search(keyword, UUID.fromString("10000000-0000-4000-8000-000000000005"),
                "ACTIVE", pageable, iamApi.getCurrentUserId()));
    }

    @GetMapping("/stats")
    public ApiResponse<AccountStatsResponse> stats() {
        return ApiResponse.ok(service.stats());
    }

    @GetMapping
    public ApiResponse<AccountPageResponse> search(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) UUID roleId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "desc") String direction) {
        Sort sort = "asc".equalsIgnoreCase(direction) ? Sort.by(sortBy).ascending() : Sort.by(sortBy).descending();
        Pageable pageable = PageRequest.of(page, size, sort);
        return ApiResponse.ok(service.search(keyword, roleId, status, pageable, iamApi.getCurrentUserId()));
    }

    @GetMapping("/{id}")
    public ApiResponse<UserAccountResponse> detail(@PathVariable UUID id) {
        return ApiResponse.ok(service.detail(id, iamApi.getCurrentUserId()));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<UserAccountResponse>> create(
            @Valid @RequestBody CreateUserAccountRequest request) {
        UserAccountResponse response = service.create(request, iamApi.getCurrentUserId());
        return ResponseEntity.status(201).body(ApiResponse.ok(response, "User account created successfully"));
    }

    @PatchMapping("/{id}")
    public ApiResponse<UserAccountResponse> update(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateUserAccountRequest request) {
        return ApiResponse.ok(service.update(id, request, iamApi.getCurrentUserId()));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<UserAccountResponse> changeStatus(
            @PathVariable UUID id,
            @Valid @RequestBody ChangeUserStatusRequest request) {
        return ApiResponse.ok(service.changeStatus(id, request, iamApi.getCurrentUserId()));
    }
}
