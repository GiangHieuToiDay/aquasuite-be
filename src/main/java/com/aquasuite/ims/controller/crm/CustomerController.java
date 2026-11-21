package com.aquasuite.ims.controller.crm;

import com.aquasuite.ims.entity.crm.Customer;
import com.aquasuite.ims.enums.crm.CustomerType;
import com.aquasuite.ims.dto.crm.request.CreateCustomerRequest;
import com.aquasuite.ims.dto.crm.request.UpdateCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileResponse;
import com.aquasuite.ims.dto.crm.response.CreditEvidenceUploadResponse;
import com.aquasuite.ims.dto.crm.response.CustomerResponse;
import com.aquasuite.ims.repository.crm.CustomerRepository;
import com.aquasuite.ims.service.crm.CustomerCreditProfileService;
import com.aquasuite.ims.service.crm.CustomerCreditProfileRequestService;
import com.aquasuite.ims.integration.crm.CreditEvidenceStorageService;
import com.aquasuite.ims.dto.crm.request.SubmitCustomerCreditProfileRequest;
import com.aquasuite.ims.dto.crm.response.CustomerCreditProfileRequestResponse;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.common.PageResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

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
    private final CustomerCreditProfileRequestService customerCreditProfileRequestService;
    private final CreditEvidenceStorageService creditEvidenceStorageService;
    private final IamQueryService iamApi;

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

    @PostMapping(value = "/{customerId}/credit-profile-evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAnyRole('SALES_STAFF','SHOP_OWNER')")
    public ResponseEntity<ApiResponse<CreditEvidenceUploadResponse>> uploadCreditProfileEvidence(
            @PathVariable UUID customerId, @RequestPart("file") MultipartFile file) {
        Customer customer = customerRepository.findById(customerId)
                .orElseThrow(() -> new com.aquasuite.ims.exception.AppException(com.aquasuite.ims.exception.crm.CrmErrorCode.CUSTOMER_NOT_FOUND));
        if (!"ACTIVE".equalsIgnoreCase(customer.getStatus())) {
            throw new com.aquasuite.ims.exception.AppException(com.aquasuite.ims.exception.crm.CrmErrorCode.CUSTOMER_NOT_ACTIVE_FOR_CREDIT);
        }
        String objectKey = creditEvidenceStorageService.upload(customerId, file);
        return ResponseEntity.ok(ApiResponse.ok(new CreditEvidenceUploadResponse(objectKey), "Credit identity evidence uploaded."));
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
