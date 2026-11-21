package com.aquasuite.ims.controller.servicedesk;

import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;
import com.aquasuite.ims.service.servicedesk.WarrantyLookupService;
import com.aquasuite.ims.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** UC-48: a customer can only query the warranty records mapped to their authenticated account. */
@RestController
@RequestMapping("/api/v1/customer/warranties")
@RequiredArgsConstructor
public class WarrantyLookupController {
    private final WarrantyLookupService warrantyLookupService;

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ApiResponse<List<WarrantyLookupResponse>> findMine(@RequestParam(required = false) String keyword) {
        return ApiResponse.ok(warrantyLookupService.findMine(keyword));
    }
}
