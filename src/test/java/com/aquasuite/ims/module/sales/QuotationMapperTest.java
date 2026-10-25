package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.CalculationLine;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator.QuotationTotals;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.mapper.QuotationMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotationMapperTest {

    private QuotationMapper mapper;
    private QuotationCalculator calculator;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = org.mapstruct.factory.Mappers.getMapper(QuotationMapper.class);
        calculator = new QuotationCalculator();
        objectMapper = new ObjectMapper().findAndRegisterModules();
    }

    @Test
    @DisplayName("5.4 - Response nhân viên (includeOwnerData = false): ownerData null, JSON không có khóa ownerData hay unitCost")
    void testStaffResponseExcludesOwnerData() throws Exception {
        UUID qId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID varId = UUID.randomUUID();

        Quotation quotation = Quotation.builder()
                .quotationId(qId)
                .quotationCode("BG-2026-0001")
                .revisionNo(1)
                .customerId(UUID.randomUUID())
                .projectName("Biệt thự Palm City")
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .createdBy(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(itemId)
                .quotationId(qId)
                .variantId(varId)
                .sku("SKU-PUMP-01")
                .unit("cái")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(2000000))
                .discountRate(BigDecimal.valueOf(5))
                .unitCost(BigDecimal.valueOf(1400000))
                .backorderQty(BigDecimal.ZERO)
                .sortOrder(1)
                .build();

        CalculationLine line = new CalculationLine(
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountRate(),
                item.getUnitCost(),
                false
        );
        QuotationTotals totals = calculator.calculate(List.of(line), quotation.getVatRate(), quotation.getShippingFee());

        DiscountPolicyChecker.DiscountCheckResult discountCheck = new DiscountPolicyChecker.DiscountCheckResult(
                false,
                BigDecimal.valueOf(5),
                BigDecimal.valueOf(10)
        );

        // Nhân viên gọi: includeOwnerData = false
        QuotationResponse staffResponse = mapper.toResponse(
                quotation,
                List.of(item),
                totals,
                discountCheck,
                "Anh Tuấn - Khách hàng",
                Map.of(varId, "Máy bơm nước biến tần thông minh"),
                false
        );

        assertNotNull(staffResponse);
        assertEquals("BG-2026-0001", staffResponse.getQuotationCode());
        assertEquals("Anh Tuấn - Khách hàng", staffResponse.getCustomerName());
        assertEquals(1, staffResponse.getItems().size());
        assertEquals("Máy bơm nước biến tần thông minh", staffResponse.getItems().get(0).getVariantName());

        // ownerData phải là null
        assertNull(staffResponse.getOwnerData());

        // Kiểm tra JSON: Không được serialize khóa "ownerData"
        String json = objectMapper.writeValueAsString(staffResponse);
        assertFalse(json.contains("\"ownerData\""), "JSON cho nhân viên không được chứa khóa ownerData");
        assertFalse(json.contains("\"grossProfit\""), "JSON cho nhân viên không được chứa khóa grossProfit");
    }

    @Test
    @DisplayName("5.4 - Response chủ cửa hàng (includeOwnerData = true): có đầy đủ ownerData, grossProfit, marginPercent, itemCosts")
    void testOwnerResponseIncludesOwnerData() throws Exception {
        UUID qId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        UUID varId = UUID.randomUUID();

        Quotation quotation = Quotation.builder()
                .quotationId(qId)
                .quotationCode("BG-2026-0002")
                .revisionNo(1)
                .customerId(UUID.randomUUID())
                .projectName("Nhà phố Sala")
                .status(QuotationStatus.APPROVED)
                .marginPercentAtApproval(BigDecimal.valueOf(25))
                .approvalNote("Duyệt giá đặc biệt theo công trình lớn")
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .createdBy(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(itemId)
                .quotationId(qId)
                .variantId(varId)
                .sku("SKU-PUMP-02")
                .unit("bộ")
                .quantity(BigDecimal.valueOf(2))
                .unitPrice(BigDecimal.valueOf(1000000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(700000))
                .backorderQty(BigDecimal.ZERO)
                .sortOrder(1)
                .build();

        CalculationLine line = new CalculationLine(
                item.getQuantity(),
                item.getUnitPrice(),
                item.getDiscountRate(),
                item.getUnitCost(),
                false
        );
        QuotationTotals totals = calculator.calculate(List.of(line), quotation.getVatRate(), quotation.getShippingFee());

        // Chủ gọi: includeOwnerData = true
        QuotationResponse ownerResponse = mapper.toResponse(
                quotation,
                List.of(item),
                totals,
                null,
                "Chị Mai",
                Map.of(varId, "Bộ lọc nước tổng biệt thự"),
                true
        );

        assertNotNull(ownerResponse.getOwnerData());
        assertEquals(0, new BigDecimal("600000.00").compareTo(ownerResponse.getOwnerData().getGrossProfit()));
        assertEquals(0, new BigDecimal("30.00").compareTo(ownerResponse.getOwnerData().getMarginPercent()));
        assertEquals(1, ownerResponse.getOwnerData().getItemCosts().size());
        assertEquals(0, new BigDecimal("700000.00").compareTo(ownerResponse.getOwnerData().getItemCosts().get(0).getUnitCost()));
        assertEquals(0, new BigDecimal("600000.00").compareTo(ownerResponse.getOwnerData().getItemCosts().get(0).getLineProfit()));

        // Kiểm tra JSON có chứa khóa ownerData
        String json = objectMapper.writeValueAsString(ownerResponse);
        assertTrue(json.contains("\"ownerData\""));
        assertTrue(json.contains("\"grossProfit\""));
    }

    @Test
    @DisplayName("5.4 - Dòng combo ra components đúng theo cấu trúc cây cha - con")
    void testComboItemComponentsTreeMapping() {
        UUID qId = UUID.randomUUID();
        UUID rootItemId = UUID.randomUUID();
        UUID childItemId1 = UUID.randomUUID();
        UUID childItemId2 = UUID.randomUUID();

        UUID rootVarId = UUID.randomUUID();
        UUID childVarId1 = UUID.randomUUID();
        UUID childVarId2 = UUID.randomUUID();

        Quotation quotation = Quotation.builder()
                .quotationId(qId)
                .quotationCode("BG-2026-COMBO")
                .revisionNo(1)
                .customerId(UUID.randomUUID())
                .projectName("Hồ bơi Thảo Điền")
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .status(QuotationStatus.DRAFT)
                .createdBy(UUID.randomUUID())
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        // 1 Dòng cha
        QuotationItem rootItem = QuotationItem.builder()
                .quotationItemId(rootItemId)
                .quotationId(qId)
                .parentItemId(null)
                .variantId(rootVarId)
                .sku("COMBO-POOL-01")
                .unit("gói")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(15000000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(10000000))
                .sortOrder(1)
                .build();

        // 2 Dòng con trỏ vào rootItemId
        QuotationItem childItem1 = QuotationItem.builder()
                .quotationItemId(childItemId1)
                .quotationId(qId)
                .parentItemId(rootItemId)
                .variantId(childVarId1)
                .sku("SKU-PUMP-COMP")
                .unit("cái")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(9000000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(6000000))
                .sortOrder(2)
                .build();

        QuotationItem childItem2 = QuotationItem.builder()
                .quotationItemId(childItemId2)
                .quotationId(qId)
                .parentItemId(rootItemId)
                .variantId(childVarId2)
                .sku("SKU-FILTER-COMP")
                .unit("bình")
                .quantity(BigDecimal.ONE)
                .unitPrice(BigDecimal.valueOf(7000000))
                .discountRate(BigDecimal.ZERO)
                .unitCost(BigDecimal.valueOf(4000000))
                .sortOrder(3)
                .build();

        CalculationLine line = new CalculationLine(
                rootItem.getQuantity(),
                rootItem.getUnitPrice(),
                rootItem.getDiscountRate(),
                rootItem.getUnitCost(),
                false
        );
        QuotationTotals totals = calculator.calculate(List.of(line), BigDecimal.TEN, BigDecimal.ZERO);

        Map<UUID, String> skuNames = Map.of(
                rootVarId, "Gói thiết bị lọc tuần hoàn hồ bơi",
                childVarId1, "Máy bơm hồ bơi 2HP",
                childVarId2, "Bình lọc cát D650"
        );

        QuotationResponse response = mapper.toResponse(
                quotation,
                List.of(rootItem, childItem1, childItem2),
                totals,
                null,
                "Khách VIP",
                skuNames,
                false
        );

        // Danh sách items chỉ chứa 1 dòng gốc
        assertEquals(1, response.getItems().size());
        assertEquals("COMBO-POOL-01", response.getItems().get(0).getSku());

        // Dòng gốc chứa 2 components con
        assertEquals(2, response.getItems().get(0).getComponents().size());
        assertEquals("SKU-PUMP-COMP", response.getItems().get(0).getComponents().get(0).getSku());
        assertEquals("Máy bơm hồ bơi 2HP", response.getItems().get(0).getComponents().get(0).getVariantName());
        assertEquals("SKU-FILTER-COMP", response.getItems().get(0).getComponents().get(1).getSku());
        assertEquals("Bình lọc cát D650", response.getItems().get(0).getComponents().get(1).getVariantName());
    }

    @Test
    @DisplayName("5.4 - Map cho màn hình danh sách QuotationListItemResponse")
    void testToListItemResponse() {
        Quotation quotation = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode("BG-2026-LIST")
                .revisionNo(2)
                .projectName("Dự án Chung cư Horizon")
                .status(QuotationStatus.SENT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .totalAmount(BigDecimal.valueOf(88000000))
                .build();

        QuotationListItemResponse listItem = mapper.toListItemResponse(
                quotation,
                "Công ty Xây dựng Hòa Bình",
                "Nguyễn Văn A (Sales)"
        );

        assertEquals("BG-2026-LIST", listItem.getQuotationCode());
        assertEquals(2, listItem.getRevisionNo());
        assertEquals("Công ty Xây dựng Hòa Bình", listItem.getCustomerName());
        assertEquals("Dự án Chung cư Horizon", listItem.getProjectName());
        assertEquals(QuotationStatus.SENT, listItem.getStatus());
        assertEquals(0, BigDecimal.valueOf(88000000).compareTo(listItem.getTotalAmount()));
        assertEquals("Nguyễn Văn A (Sales)", listItem.getCreatedByName());
    }
}
