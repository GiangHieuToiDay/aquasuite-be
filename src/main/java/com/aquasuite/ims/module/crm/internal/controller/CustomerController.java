package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
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
                .status(c.getStatus())
                .createdAt(c.getCreatedAt())
                .updatedAt(c.getUpdatedAt())
                .build();
    }
}
