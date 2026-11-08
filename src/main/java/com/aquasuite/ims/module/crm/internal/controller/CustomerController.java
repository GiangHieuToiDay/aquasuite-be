package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerResponse;
import com.aquasuite.ims.module.crm.internal.payload.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileService;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileRequestService;
import com.aquasuite.ims.module.crm.internal.payload.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Controller phục vụ tra cứu thông tin Khách hàng cho Sales Quotations, CRM và Đơn hàng.
 */
@Slf4j
@RestController
@RequestMapping({"/api/v1/crm/customers", "/api/v1/customers"})
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerRepository customerRepository;
    private final CustomerCreditProfileService customerCreditProfileService;
    private final CustomerCreditProfileRequestService customerCreditProfileRequestService;
    private final IamApi iamApi;

    /**
     * Lấy danh sách khách hàng hoạt động (ACTIVE) phục vụ cho dropdown/select khi lập báo giá.
     */
    @GetMapping("/options")
    public ResponseEntity<ApiResponse<List<CustomerResponse>>> getCustomerOptions() {
        List<CustomerResponse> list = customerRepository.findByStatusIgnoreCaseOrderByCustomerNameAsc("ACTIVE")
                .stream()
                .map(this::toResponse)
                .toList();
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * Tra cứu và phân trang danh sách khách hàng.
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CustomerResponse>>> listCustomers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        String keyword = (search != null && !search.isBlank()) ? search.trim() : null;
        String filterStatus = (status != null && !status.isBlank()) ? status.trim() : null;

        Page<Customer> page = customerRepository.searchCustomers(keyword, filterStatus, pageable);
        Page<CustomerResponse> mappedPage = page.map(this::toResponse);
        return ResponseEntity.ok(ApiResponse.ok(PageResponse.from(mappedPage)));
    }

    /**
     * Lấy chi tiết thông tin một khách hàng theo ID.
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomerById(@PathVariable UUID customerId) {
        return customerRepository.findById(customerId)
                .map(c -> ResponseEntity.ok(ApiResponse.ok(toResponse(c))))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Legacy compatibility endpoint. Sales Staff must use the durable credit-profile request flow instead.
     */
    @PatchMapping("/{customerId}/credit-profile")
    @Deprecated(since = "UC-24 credit profile approval workflow")
    @PreAuthorize("hasRole('SHOP_OWNER')")
    public ResponseEntity<ApiResponse<CustomerCreditProfileResponse>> updateCreditProfile(
            @PathVariable UUID customerId,
            @jakarta.validation.Valid @RequestBody UpdateCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerCreditProfileService.update(customerId,
                iamApi.getCurrentUserId(), request), "Customer credit profile updated."));
    }

    @PostMapping("/{customerId}/credit-profile-requests")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<CustomerCreditProfileRequestResponse>> submitCreditProfileRequest(
            @PathVariable UUID customerId,
            @jakarta.validation.Valid @RequestBody SubmitCustomerCreditProfileRequest request) {
        CustomerCreditProfileRequestResponse result = customerCreditProfileRequestService.submit(customerId, request);
        String message = result.appliedImmediately()
                ? "Customer credit profile applied."
                : "Credit profile request submitted for Shop Owner approval.";
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(result, message));
    }

    private CustomerResponse toResponse(Customer c) {
        String addressStr = (c.getAddress() != null && c.getAddress().length > 0)
                ? String.join(", ", c.getAddress())
                : null;

        return CustomerResponse.builder()
                .customerId(c.getCustomerId())
                .userId(c.getUserId())
                .customerCode(c.getCustomerCode())
                .customerName(c.getCustomerName())
                .phone(c.getPhone())
                .email(c.getEmail())
                .customerType(c.getCustomerType())
                .taxCode(c.getTaxCode())
                .companyName(c.getCompanyName())
                .address(addressStr)
                .creditLimit(c.getCreditLimit())
                .creditTermDays(c.getCreditTermDays())
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
