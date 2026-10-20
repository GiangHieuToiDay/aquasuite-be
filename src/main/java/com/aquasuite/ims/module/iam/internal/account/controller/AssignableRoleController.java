package com.aquasuite.ims.module.iam.internal.account.controller;

import com.aquasuite.ims.module.iam.internal.account.dto.response.RoleOptionResponse;
import com.aquasuite.ims.module.iam.internal.account.service.UserAccountService;
import com.aquasuite.ims.share.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import lombok.AccessLevel;
import lombok.experimental.FieldDefaults;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Provides only roles that may be assigned through the account-management form. */
@RestController
@RequestMapping("/api/v1/iam/roles")
@RequiredArgsConstructor
@PreAuthorize("hasRole('STORE_OWNER')")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
public class AssignableRoleController {
    UserAccountService service;

    @GetMapping
    public ApiResponse<List<RoleOptionResponse>> roles(@RequestParam(defaultValue = "true") boolean assignable) {
        return ApiResponse.ok(service.assignableRoles());
    }
}
