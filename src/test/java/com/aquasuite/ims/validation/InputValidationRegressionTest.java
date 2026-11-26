package com.aquasuite.ims.validation;

import com.aquasuite.ims.common.ApiResponse;
import com.aquasuite.ims.dto.catalog.request.CreateProductRequest;
import com.aquasuite.ims.dto.catalog.request.ProductSearchCriteria;
import com.aquasuite.ims.dto.crm.request.UpdateProfileRequest;
import com.aquasuite.ims.dto.finance.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.dto.finance.request.DebtCallResultRequest;
import com.aquasuite.ims.dto.purchasing.request.CreatePurchaseOrderRequest;
import com.aquasuite.ims.dto.sales.request.AddCartItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateComboItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateOrderItemRequest;
import com.aquasuite.ims.dto.sales.request.CreateSalesOrderRequest;
import com.aquasuite.ims.dto.sales.request.LinkElectronicInvoiceRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.UpsertWarrantyPolicyRequest;
import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.enums.servicedesk.WarrantyPolicyStatus;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import com.aquasuite.ims.repository.catalog.BrandRepository;
import com.aquasuite.ims.repository.catalog.CategoryRepository;
import com.aquasuite.ims.repository.catalog.ProductImageRepository;
import com.aquasuite.ims.repository.catalog.ProductRepository;
import com.aquasuite.ims.repository.catalog.ProductVariantRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyRecordRepository;
import com.aquasuite.ims.repository.servicedesk.WarrantyTicketRepository;
import com.aquasuite.ims.service.catalog.CatalogWarrantyLookupService;
import com.aquasuite.ims.service.catalog.impl.ProductStorefrontServiceImpl;
import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.integration.servicedesk.FulfillmentWarrantyPort;
import com.aquasuite.ims.service.servicedesk.helper.WarrantyCodeGenerator;
import com.aquasuite.ims.service.servicedesk.impl.WarrantyRecordServiceImpl;
import com.aquasuite.ims.util.PageableUtils;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class InputValidationRegressionTest {

    private static Validator validator;

    @BeforeAll
    static void initValidator() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    void globalExceptionHandler_handlesConstraintViolationAndPropertyReference() {
        GlobalExceptionHandler handler = new GlobalExceptionHandler();

        CreateProductRequest badProduct = CreateProductRequest.builder()
                .productCode(" ")
                .productName("Valid")
                .categoryId(UUID.randomUUID())
                .imageUrls(List.of("ftp://invalid-scheme/img.png"))
                .build();
        Set<ConstraintViolation<CreateProductRequest>> violations = validator.validate(badProduct);
        ResponseEntity<ApiResponse<Void>> cvResponse =
                handler.handleConstraintViolationException(new ConstraintViolationException(violations));

        assertThat(cvResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(cvResponse.getBody()).isNotNull();
        assertThat(cvResponse.getBody().getCode()).isEqualTo("SYS_003");
        assertThat(cvResponse.getBody().getErrors()).isNotEmpty();

        PropertyReferenceException propEx = mock(PropertyReferenceException.class);
        ResponseEntity<ApiResponse<Void>> propResponse = handler.handleInvalidRequestParamException(propEx);

        assertThat(propResponse.getStatusCode().value()).isEqualTo(400);
        assertThat(propResponse.getBody()).isNotNull();
        assertThat(propResponse.getBody().getCode()).isEqualTo("SYS_002");
    }

    @Test
    void pageableUtils_sanitizePageable_preservesValidInstanceCapsSizeAndRejectsBadSort() {
        Pageable valid = PageRequest.of(0, 10, Sort.by(Sort.Direction.DESC, "createdAt"));
        Pageable preserved = PageableUtils.sanitizePageable(valid, Set.of("createdAt", "orderDate"));
        assertThat(preserved).isSameAs(valid);

        Pageable oversized = PageRequest.of(0, 500, Sort.by("createdAt"));
        Pageable capped = PageableUtils.sanitizePageable(oversized, Set.of("createdAt"));
        assertThat(capped.getPageSize()).isEqualTo(100);
        assertThat(capped.getSort()).isEqualTo(oversized.getSort());

        Pageable badSort = PageRequest.of(0, 20, Sort.by("sqlInjectionField"));
        assertThatThrownBy(() -> PageableUtils.sanitizePageable(badSort, Set.of("createdAt")))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM);

        Pageable mapped = PageableUtils.sanitizePageable(
                PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")),
                Map.of("createdAt", "created_at"));
        assertThat(mapped.getSort().getOrderFor("created_at")).isNotNull();

        assertThatThrownBy(() -> PageableUtils.validateDateRange(
                LocalDate.of(2026, 12, 10), LocalDate.of(2026, 12, 1)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM);
        PageableUtils.validateDateRange(LocalDate.of(2026, 12, 1), LocalDate.of(2026, 12, 1));
    }

    @Test
    void salesAndInventoryDtos_rejectNegativeDiscountsNullElementsAndExcessivePrecision() {
        CreateSalesOrderRequest badOrder = CreateSalesOrderRequest.builder()
                .discountAmount(new BigDecimal("-1.00"))
                .items(Collections.singletonList(null))
                .build();
        assertThat(validator.validate(badOrder))
                .anyMatch(v -> v.getPropertyPath().toString().equals("discountAmount"))
                .anyMatch(v -> v.getPropertyPath().toString().startsWith("items"));

        CreateOrderItemRequest badOrderItem = CreateOrderItemRequest.builder()
                .variantId(UUID.randomUUID())
                .quantity(new BigDecimal("1.000"))
                .discountAmount(new BigDecimal("-500"))
                .build();
        assertThat(validator.validate(badOrderItem))
                .anyMatch(v -> v.getPropertyPath().toString().equals("discountAmount"));

        CreateComboItemRequest badCombo = CreateComboItemRequest.builder()
                .comboId(UUID.randomUUID())
                .quantity(20000)
                .discountAmount(new BigDecimal("-10"))
                .build();
        assertThat(validator.validate(badCombo))
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"))
                .anyMatch(v -> v.getPropertyPath().toString().equals("discountAmount"));

        AddCartItemRequest badCartItem = AddCartItemRequest.builder()
                .variantId(UUID.randomUUID())
                .quantity(new BigDecimal("1.234"))
                .build();
        assertThat(validator.validate(badCartItem))
                .anyMatch(v -> v.getPropertyPath().toString().equals("quantity"));

        CreatePurchaseOrderRequest badPo = CreatePurchaseOrderRequest.builder()
                .supplierId(UUID.randomUUID())
                .expectedDate(LocalDate.now().minusDays(1))
                .items(Collections.singletonList(null))
                .build();
        assertThat(validator.validate(badPo))
                .anyMatch(v -> v.getPropertyPath().toString().equals("expectedDate"))
                .anyMatch(v -> v.getPropertyPath().toString().startsWith("items"));

        CreatePaymentAllocationRequest badAlloc = CreatePaymentAllocationRequest.builder()
                .allocationRequestId(UUID.randomUUID())
                .allocations(Collections.singletonList(null))
                .build();
        assertThat(validator.validate(badAlloc))
                .anyMatch(v -> v.getPropertyPath().toString().startsWith("allocations"));
    }

    @Test
    void datesUrlsAndWarrantyBounds_areValidatedOnDtosAndServices() {
        CreateWarrantyTicketRequest badTicket = CreateWarrantyTicketRequest.builder()
                .warrantyRecordId(UUID.randomUUID())
                .issueDescription("ngắn")
                .preferredDate(LocalDate.now().minusDays(1))
                .siteAddress("123 Nguyễn Trãi")
                .media(Collections.singletonList((TicketMedia) null))
                .build();
        assertThat(validator.validate(badTicket))
                .anyMatch(v -> v.getPropertyPath().toString().equals("issueDescription"))
                .anyMatch(v -> v.getPropertyPath().toString().equals("preferredDate"))
                .anyMatch(v -> v.getPropertyPath().toString().startsWith("media"));

        UpsertWarrantyPolicyRequest badPolicy = UpsertWarrantyPolicyRequest.builder()
                .policyName("Chính sách 51 năm")
                .brandId(UUID.randomUUID())
                .categoryId(UUID.randomUUID())
                .warrantyPeriodMonths(601)
                .coverageConditions("Bảo hành")
                .status(WarrantyPolicyStatus.ACTIVE)
                .build();
        assertThat(validator.validate(badPolicy))
                .anyMatch(v -> v.getPropertyPath().toString().equals("warrantyPeriodMonths"));

        CreateWarrantyFromOrderRequest badWarrantyOrder = new CreateWarrantyFromOrderRequest(
                UUID.randomUUID(), "SN-1", LocalDate.now().plusDays(1));
        assertThat(validator.validate(badWarrantyOrder))
                .anyMatch(v -> v.getPropertyPath().toString().equals("startDate"));

        LinkElectronicInvoiceRequest badEInvoice = new LinkElectronicInvoiceRequest(
                "VNPT", "INV-1", "1C26T", OffsetDateTime.now().plusDays(1));
        assertThat(validator.validate(badEInvoice))
                .anyMatch(v -> v.getPropertyPath().toString().equals("signedAt"));

        DebtCallResultRequest badDebtCall = new DebtCallResultRequest(
                DebtCallResultRequest.CallResult.PROMISED_TO_PAY, LocalDate.now().minusDays(1), "note");
        assertThat(validator.validate(badDebtCall))
                .anyMatch(v -> v.getPropertyPath().toString().equals("promisedPaymentDate"));

        UpdateProfileRequest badProfile = UpdateProfileRequest.builder()
                .fullName("Nguyễn Văn A")
                .phone("0901234567")
                .avatarUrl("javascript:alert(1)")
                .build();
        assertThat(validator.validate(badProfile))
                .anyMatch(v -> v.getPropertyPath().toString().equals("avatarUrl"));

        Clock fixedClock = Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), ZoneId.of("Asia/Ho_Chi_Minh"));
        WarrantyRecordServiceImpl warrantyService = new WarrantyRecordServiceImpl(
                mock(WarrantyRecordRepository.class),
                mock(WarrantyTicketRepository.class),
                mock(WarrantyCodeGenerator.class),
                null,
                mock(CatalogWarrantyLookupService.class),
                null,
                null,
                mock(FulfillmentWarrantyPort.class),
                null,
                fixedClock,
                null);

        assertThatThrownBy(() -> warrantyService.createFromOrder(
                new CreateWarrantyFromOrderRequest(UUID.randomUUID(), null, LocalDate.of(2026, 11, 13))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);

        assertThatThrownBy(() -> warrantyService.createAdHoc(
                new CreateAdHocWarrantyRequest(UUID.randomUUID(), UUID.randomUUID(), null, null, LocalDate.of(1999, 12, 31))))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.VALIDATION_ERROR);

        ProductStorefrontServiceImpl storefrontService = new ProductStorefrontServiceImpl(
                mock(ProductRepository.class),
                mock(ProductVariantRepository.class),
                mock(ProductImageRepository.class),
                mock(CategoryRepository.class),
                mock(BrandRepository.class),
                mock(InventoryService.class));

        assertThatThrownBy(() -> storefrontService.searchProducts(
                ProductSearchCriteria.builder()
                        .minPrice(new BigDecimal("500000"))
                        .maxPrice(new BigDecimal("100000"))
                        .build(),
                PageRequest.of(0, 20)))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM);
    }
}
