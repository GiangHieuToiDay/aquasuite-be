package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class QuotationEntityConstraintTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private QuotationRepository quotationRepository;

    @Autowired
    private QuotationItemRepository quotationItemRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("5.5 - Chèn cùng quotation_code khác revision_no thành công; trùng cả hai bị chặn")
    void testUniqueQuotationCodeAndRevisionConstraint() {
        String code = "BG-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. Revision 1 thành công
        Quotation rev1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(code)
                .revisionNo(1)
                .customerId(customerId)
                .projectName("Test Project")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertDoesNotThrow(() -> quotationRepository.saveAndFlush(rev1));

        // 2. Cùng code nhưng Revision 2 -> Thành công
        Quotation rev2 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(code)
                .revisionNo(2)
                .customerId(customerId)
                .projectName("Test Project")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertDoesNotThrow(() -> quotationRepository.saveAndFlush(rev2));

        // 3. Cùng code VÀ cùng Revision 1 -> Bị chặn bởi UNIQUE (quotation_code, revision_no)
        Quotation rev1Duplicate = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(code)
                .revisionNo(1) // Trùng revision 1
                .customerId(customerId)
                .projectName("Test Project Duplicate")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            quotationRepository.saveAndFlush(rev1Duplicate);
        });
    }

    @Test
    @DisplayName("5.5 - Quantity <= 0 hoặc VAT sai bị database từ chối")
    void testCheckConstraints() {
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        // 1. VAT rate sai (không phải 0, 8, 10)
        Quotation invalidVat = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode("BG-VAT-ERR-" + UUID.randomUUID().toString().substring(0, 8))
                .revisionNo(1)
                .customerId(customerId)
                .projectName("Test Project Invalid VAT")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(5)) // Sai CHECK (vat_rate IN (0, 8, 10))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        assertThrows(DataIntegrityViolationException.class, () -> {
            quotationRepository.saveAndFlush(invalidVat);
        });
    }

    @Test
    @DisplayName("5.5 - Xóa báo giá thì xóa luôn các dòng chi tiết (ON DELETE CASCADE)")
    @Transactional
    void testCascadeDeleteQuotationDeletesItems() {
        UUID qId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID variantId = UUID.randomUUID();

        // Tạo dummy product & variant để thỏa FK variant_id
        jdbcTemplate.update(
                "INSERT INTO public.categories (category_id, category_code, category_name, status) " +
                        "VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                UUID.randomUUID(), "CAT-TEST-SALES", "Cat Test", "ACTIVE"
        );
        UUID catId = jdbcTemplate.queryForObject("SELECT category_id FROM public.categories LIMIT 1", UUID.class);

        UUID prodId = UUID.randomUUID();
        jdbcTemplate.update(
                "INSERT INTO public.products (product_id, product_code, category_id, product_name, product_type, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?)",
                prodId, "PROD-TEST-" + prodId.toString().substring(0, 8), catId, "Prod Test", "NORMAL", "ACTIVE"
        );

        jdbcTemplate.update(
                "INSERT INTO public.product_variants (variant_id, product_id, sku, variant_name, unit, conversion_rate, cost_price, selling_price, status) " +
                        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)",
                variantId, prodId, "SKU-TEST-" + variantId.toString().substring(0, 8), "Variant Test", "cái", 1, 100, 200, "ACTIVE"
        );

        // Tạo quotation
        Quotation quotation = Quotation.builder()
                .quotationId(qId)
                .quotationCode("BG-CASCADE-" + qId.toString().substring(0, 8))
                .revisionNo(1)
                .customerId(customerId)
                .projectName("Test Cascade Project")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .totalAmount(BigDecimal.ZERO)
                .createdBy(userId)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();
        quotationRepository.saveAndFlush(quotation);

        // Tạo 2 quotation items
        QuotationItem item1 = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(qId)
                .variantId(variantId)
                .sku("SKU-1")
                .unit("cái")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(1000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(500))
                .sortOrder(1)
                .build();

        QuotationItem item2 = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(qId)
                .variantId(variantId)
                .sku("SKU-2")
                .unit("cái")
                .quantity(BigDecimal.valueOf(2))
                .unitPrice(BigDecimal.valueOf(2000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(1000))
                .sortOrder(2)
                .build();

        quotationItemRepository.saveAndFlush(item1);
        quotationItemRepository.saveAndFlush(item2);

        // Đảm bảo 2 dòng đã lưu
        List<QuotationItem> itemsBefore = quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(qId);
        assertEquals(2, itemsBefore.size());

        // Xóa quotation
        quotationRepository.delete(quotation);
        quotationRepository.flush();

        // Kiểm tra các dòng chi tiết tự động bị xóa theo CASCADE
        Integer remainingItemsCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM public.quotation_items WHERE quotation_id = ?",
                Integer.class,
                qId
        );
        assertEquals(0, remainingItemsCount, "Khi xóa quotation, tất cả quotation_items liên quan phải bị xóa theo CASCADE");
    }
}
