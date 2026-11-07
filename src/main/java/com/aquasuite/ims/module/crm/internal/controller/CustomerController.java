package com.aquasuite.ims.module.crm.internal.controller;

import com.aquasuite.ims.module.crm.internal.entity.Customer;
import com.aquasuite.ims.module.crm.internal.enums.CustomerType;
import com.aquasuite.ims.module.crm.internal.payload.request.CreateCustomerRequest;
import com.aquasuite.ims.module.crm.internal.payload.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerCreditProfileResponse;
import com.aquasuite.ims.module.crm.internal.payload.res.CustomerResponse;
import com.aquasuite.ims.module.crm.internal.repository.CustomerRepository;
import com.aquasuite.ims.module.crm.internal.service.CustomerCreditProfileService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.share.dto.ApiResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
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
     * Tạo mới thông tin khách hàng vào danh mục CRM (phục vụ quầy POS hoặc bán hàng).
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<CustomerResponse>> createCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        String phone = (request.phone() != null && !request.phone().isBlank()) ? request.phone().trim() : null;

        if (phone != null && customerRepository.existsByPhone(phone)) {
            Customer existing = customerRepository.findByPhone(phone).orElse(null);
            String existingName = existing != null ? existing.getCustomerName() : "";
            return ResponseEntity.badRequest().body(ApiResponse.error(
                    "CUSTOMER_PHONE_EXISTS",
                    "Số điện thoại này đã được sử dụng bởi khách hàng: " + existingName + " (" + (existing != null ? existing.getCustomerCode() : "") + ")"));
        }

        String customerCode = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        while (customerRepository.existsByCustomerCode(customerCode)) {
            customerCode = "CUS-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        }

        String[] addresses = null;
        if (request.address() != null && !request.address().isBlank()) {
            addresses = new String[]{request.address().trim()};
        }

        Customer newCustomer = Customer.builder()
                .customerId(UUID.randomUUID())
                .customerCode(customerCode)
                .customerName(request.customerName().trim())
                .phone(phone != null ? phone : "")
                .email(request.email() != null && !request.email().isBlank() ? request.email().trim() : null)
                .customerType(request.customerType() != null ? request.customerType() : CustomerType.RETAIL)
                .taxCode(request.taxCode() != null && !request.taxCode().isBlank() ? request.taxCode().trim() : null)
                .companyName(request.companyName() != null && !request.companyName().isBlank() ? request.companyName().trim() : null)
                .address(addresses)
                .status("ACTIVE")
                .creditLimit(BigDecimal.ZERO)
                .build();

        Customer saved = customerRepository.save(newCustomer);
        log.info("Created new customer {} ({}) by user {}", saved.getCustomerName(), saved.getCustomerCode(), iamApi.getCurrentUserId());
        return ResponseEntity.ok(ApiResponse.ok(toResponse(saved), "Tạo khách hàng mới thành công"));
    }

    /** Consumes a short-lived Shop Owner approval grant while preserving the initiating Sales Staff identity. */
    @PatchMapping("/{customerId}/credit-profile")
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<CustomerCreditProfileResponse>> updateCreditProfile(
            @PathVariable UUID customerId,
            @jakarta.validation.Valid @RequestBody UpdateCustomerCreditProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.ok(customerCreditProfileService.update(customerId,
                iamApi.getCurrentUserId(), request), "Customer credit profile updated."));
    }

    private CustomerResponse toResponse(Customer c) {
        String addressStr = (c.getAddress() != null && c.getAddress().length > 0)
                ? String.join(", ", c.getAddress())
                : null;

        String name = c.getCustomerName();
        if (name == null || name.isBlank() || "Walk-in Retail Customer".equalsIgnoreCase(name.trim())) {
            name = "Khách lẻ tại quầy";
        }
        String phone = c.getPhone();
        if (phone == null || phone.isBlank() || "0000000000".equals(phone.trim())) {
            phone = null;
        }

        return CustomerResponse.builder()
                .customerId(c.getCustomerId())
                .userId(c.getUserId())
                .customerCode(c.getCustomerCode())
                .customerName(name)
                .phone(phone)
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
