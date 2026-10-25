package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.payload.request.QuotationItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationValidationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @Test
    @DisplayName("5.3 - Request hợp lệ với đầy đủ trường bắt buộc và danh sách items")
    void testValidRequest() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(UUID.randomUUID())
                .projectName("Dự án cấp nước Villa Riverside")
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.valueOf(100000))
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(UUID.randomUUID())
                                .quantity(BigDecimal.valueOf(2))
                                .discountRate(BigDecimal.valueOf(5))
                                .build()
                ))
                .build();

        Set<ConstraintViolation<QuotationUpsertRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Request hợp lệ không được có lỗi validation");
    }

    @Test
    @DisplayName("5.3 - Items rỗng vẫn hợp lệ (vì lưu nháp không bắt buộc đủ dòng)")
    void testEmptyItemsIsValid() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(UUID.randomUUID())
                .projectName("Dự án nháp chưa chọn hàng")
                .vatRate(BigDecimal.valueOf(8))
                .items(new ArrayList<>())
                .build();

        Set<ConstraintViolation<QuotationUpsertRequest>> violations = validator.validate(request);
        assertTrue(violations.isEmpty(), "Items rỗng vẫn phải hợp lệ khi lưu nháp");
    }

    @Test
    @DisplayName("5.3 - Thiếu trường bắt buộc (customerId null, projectName blank) -> Bị từ chối")
    void testMissingRequiredFields() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(null)
                .projectName("   ")
                .build();

        Set<ConstraintViolation<QuotationUpsertRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("customerId")));
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("projectName")));
    }

    @Test
    @DisplayName("5.3 - VAT ngoài 0/8/10 (ví dụ 5% hoặc 12%) -> Bị từ chối bởi @AllowedVatRate")
    void testInvalidVatRateRejected() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(UUID.randomUUID())
                .projectName("Dự án lỗi thuế")
                .vatRate(BigDecimal.valueOf(5)) // Không thuộc {0, 8, 10}
                .build();

        Set<ConstraintViolation<QuotationUpsertRequest>> violations = validator.validate(request);
        assertFalse(violations.isEmpty());
        assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("vatRate")));
    }

    @Test
    @DisplayName("5.3 - Chiết khấu ngoài 0 đến 100 hoặc Số lượng <= 0 -> Bị từ chối")
    void testInvalidItemQuantityAndDiscountRate() {
        // Quantity <= 0
        QuotationItemRequest zeroQtyItem = QuotationItemRequest.builder()
                .variantId(UUID.randomUUID())
                .quantity(BigDecimal.ZERO)
                .discountRate(BigDecimal.valueOf(10))
                .build();

        Set<ConstraintViolation<QuotationItemRequest>> qtyViolations = validator.validate(zeroQtyItem);
        assertFalse(qtyViolations.isEmpty());
        assertTrue(qtyViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("quantity")));

        // DiscountRate > 100
        QuotationItemRequest highDiscountItem = QuotationItemRequest.builder()
                .variantId(UUID.randomUUID())
                .quantity(BigDecimal.ONE)
                .discountRate(BigDecimal.valueOf(105))
                .build();

        Set<ConstraintViolation<QuotationItemRequest>> discountViolations = validator.validate(highDiscountItem);
        assertFalse(discountViolations.isEmpty());
        assertTrue(discountViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discountRate")));

        // DiscountRate < 0
        QuotationItemRequest negativeDiscountItem = QuotationItemRequest.builder()
                .variantId(UUID.randomUUID())
                .quantity(BigDecimal.ONE)
                .discountRate(BigDecimal.valueOf(-5))
                .build();

        Set<ConstraintViolation<QuotationItemRequest>> negDiscountViolations = validator.validate(negativeDiscountItem);
        assertFalse(negDiscountViolations.isEmpty());
        assertTrue(negDiscountViolations.stream().anyMatch(v -> v.getPropertyPath().toString().equals("discountRate")));
    }
}
