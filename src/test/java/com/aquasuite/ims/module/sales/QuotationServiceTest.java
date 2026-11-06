package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerAddressDto;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.internal.calculator.DiscountPolicyChecker;
import com.aquasuite.ims.module.sales.internal.calculator.QuotationCalculator;
import com.aquasuite.ims.module.sales.internal.entity.Quotation;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.enums.QuotationStatus;
import com.aquasuite.ims.module.sales.internal.exception.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.mapper.QuotationMapper;
import com.aquasuite.ims.module.sales.internal.payload.request.ApproveQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkAcceptedRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkLostRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationItemRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RejectQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SubmitApprovalRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.module.sales.internal.repository.QuotationItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationPaymentMilestoneRepository;
import com.aquasuite.ims.module.sales.internal.repository.QuotationRepository;
import com.aquasuite.ims.module.sales.internal.service.QuotationService;
import com.aquasuite.ims.module.sales.internal.service.impl.QuotationServiceImpl;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.share.dto.PageResponse;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuotationServiceTest {

    @Mock
    private CatalogApi catalogApi;

    @Mock
    private CrmApi crmApi;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private IamApi iamApi;

    @Mock
    private QuotationRepository quotationRepository;

    @Mock
    private QuotationItemRepository quotationItemRepository;

    @Mock
    private QuotationPaymentMilestoneRepository quotationPaymentMilestoneRepository;

    private QuotationService quotationService;

    private final UUID customerId = UUID.randomUUID();
    private final UUID currentUserId = UUID.randomUUID();
    private final UUID variantId1 = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        QuotationCalculator calculator = new QuotationCalculator();
        DiscountPolicyChecker discountChecker = new DiscountPolicyChecker();
        QuotationMapper mapper = Mappers.getMapper(QuotationMapper.class);

        quotationService = new QuotationServiceImpl(
                catalogApi,
                crmApi,
                inventoryApi,
                iamApi,
                calculator,
                discountChecker,
                mapper,
                quotationRepository,
                quotationItemRepository,
                quotationPaymentMilestoneRepository
        );
    }

    @Test
    @DisplayName("Tạo báo giá thành công với đầy đủ sản phẩm, tính toán tài chính và lưu DRAFT")
    void testCreateQuotation_SuccessWithItems() {
        // Arrange
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án Tòa nhà Bitexco")
                .deliveryAddress("Số 2 Hải Triều, Q.1, TP.HCM")
                .validUntil(LocalDate.now().plusDays(30))
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.valueOf(500000))
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.valueOf(5))
                                .discountRate(BigDecimal.valueOf(5))
                                .build()
                ))
                .build();

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerCode("CUS-001")
                .customerName("Tập đoàn Bitexco")
                .status("ACTIVE")
                .build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        CatalogSkuSummaryDto skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .variantName("Máy bơm công nghiệp 3HP")
                .unit("cái")
                .sellingPrice(BigDecimal.valueOf(10000000))
                .costPrice(BigDecimal.valueOf(7000000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, skuDto));

        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId1)
                .availableQty(BigDecimal.valueOf(10))
                .build();
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, stockDto));

        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        when(quotationRepository.findTopByQuotationCodeStartingWithOrderByQuotationCodeDesc(anyString()))
                .thenReturn(Optional.empty());

        when(quotationRepository.save(any(Quotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        QuotationResponse response = quotationService.createQuotation(request);

        // Assert
        assertNotNull(response);
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
        assertEquals(1, response.getRevisionNo());
        assertTrue(response.getQuotationCode().startsWith("QT-"));
        assertEquals("Tập đoàn Bitexco", response.getCustomerName());
        assertEquals(1, response.getItems().size());

        // Kiểm tra tính toán: 5 * 10,000,000 = 50,000,000. Giảm 5% = 2,500,000. Net = 47,500,000. VAT 10% = 4,750,000. Ship = 500,000. Total = 52,750,000.
        assertNotNull(response.getSummary());
        assertEquals(0, new BigDecimal("50000000.00").compareTo(response.getSummary().getListPriceTotal()));
        assertEquals(0, new BigDecimal("2500000.00").compareTo(response.getSummary().getTotalDiscount()));
        assertEquals(0, new BigDecimal("47500000.00").compareTo(response.getSummary().getNetAmount()));
        assertEquals(0, new BigDecimal("4750000.00").compareTo(response.getSummary().getVatAmount()));
        assertEquals(0, new BigDecimal("52750000.00").compareTo(response.getSummary().getGrandTotal()));

        // Backorder phải bằng 0 do tồn kho có 10 mà chỉ đặt 5
        assertEquals(0, BigDecimal.ZERO.compareTo(response.getItems().get(0).getBackorderQty()));
        assertFalse(response.getItems().get(0).getIsBackorder());

        // Nhân viên gọi -> ownerData phải là null
        assertNull(response.getOwnerData());

        verify(quotationRepository, times(1)).save(any(Quotation.class));
        verify(quotationItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("Tạo báo giá thành công khi items rỗng (Lưu nháp ban đầu)")
    void testCreateQuotation_EmptyItems() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án đang lập")
                .shippingFee(BigDecimal.valueOf(100000))
                .items(Collections.emptyList())
                .build();

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerName("Khách lẻ")
                .status("ACTIVE")
                .build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuotationResponse response = quotationService.createQuotation(request);

        assertNotNull(response);
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
        assertTrue(response.getItems().isEmpty());
        assertEquals(0, new BigDecimal("100000.00").compareTo(response.getSummary().getGrandTotal()));

        verify(quotationItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("Tính toán Backorder chính xác khi số lượng đặt vượt quá tồn khả dụng")
    void testCreateQuotation_BackorderCalculated() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án thiếu hàng")
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.valueOf(15))
                                .discountRate(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerName("Khách dự án")
                .status("ACTIVE")
                .build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        CatalogSkuSummaryDto skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-PUMP-02")
                .unit("cái")
                .sellingPrice(BigDecimal.valueOf(1000000))
                .costPrice(BigDecimal.valueOf(700000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, skuDto));

        // Tồn chỉ có 4 mà đặt 15 -> thiếu 11
        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId1)
                .availableQty(BigDecimal.valueOf(4))
                .build();
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, stockDto));

        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuotationResponse response = quotationService.createQuotation(request);

        assertEquals(1, response.getItems().size());
        assertEquals(0, new BigDecimal("11.00").compareTo(response.getItems().get(0).getBackorderQty()));
        assertTrue(response.getItems().get(0).getIsBackorder());
        assertEquals(7, response.getItems().get(0).getLeadTimeDays());
    }

    @Test
    @DisplayName("Chủ cửa hàng (Admin) gọi API -> Response có đầy đủ thông tin ownerData (giá vốn & lợi nhuận)")
    void testCreateQuotation_OwnerGetsOwnerData() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án xem lợi nhuận")
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.valueOf(2))
                                .discountRate(BigDecimal.ZERO)
                                .build()
                ))
                .build();

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerName("Khách VIP")
                .status("ACTIVE")
                .build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        CatalogSkuSummaryDto skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-01")
                .unit("bộ")
                .sellingPrice(BigDecimal.valueOf(1000000))
                .costPrice(BigDecimal.valueOf(600000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, skuDto));
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Collections.emptyMap());

        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("owner").roles(List.of("SHOP_OWNER")).build()); // Là chủ cửa hàng
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        QuotationResponse response = quotationService.createQuotation(request);

        assertNotNull(response.getOwnerData());
        assertEquals(0, new BigDecimal("800000.00").compareTo(response.getOwnerData().getGrossProfit()));
        assertEquals(0, new BigDecimal("40.00").compareTo(response.getOwnerData().getMarginPercent()));
    }

    @Test
    @DisplayName("Khách hàng không tồn tại -> Ném lỗi CUSTOMER_NOT_FOUND")
    void testCreateQuotation_CustomerNotFound() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án A")
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Khách hàng bị khóa (INACTIVE) -> Ném lỗi CUSTOMER_NOT_ACTIVE")
    void testCreateQuotation_CustomerInactive() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án B")
                .build();

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerCode("CUS-INACTIVE")
                .status("INACTIVE")
                .build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.CUSTOMER_NOT_ACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Biến thể SKU không tồn tại -> Ném lỗi SKU_NOT_FOUND")
    void testCreateQuotation_SkuNotFound() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án C")
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()
        ));
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Collections.emptyMap());

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.SKU_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("Biến thể SKU ngừng kinh doanh -> Ném lỗi SKU_INACTIVE")
    void testCreateQuotation_SkuInactive() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án D")
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()
        ));
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(
                variantId1, CatalogSkuSummaryDto.builder()
                        .variantId(variantId1)
                        .sku("SKU-OLD")
                        .status("INACTIVE")
                        .build()
        ));

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.SKU_INACTIVE, ex.getErrorCode());
    }

    @Test
    @DisplayName("Trùng SKU trong danh sách dòng -> Ném lỗi DUPLICATE_SKU_ITEM")
    void testCreateQuotation_DuplicateSku() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án E")
                .items(List.of(
                        QuotationItemRequest.builder().variantId(variantId1).quantity(BigDecimal.ONE).build(),
                        QuotationItemRequest.builder().variantId(variantId1).quantity(BigDecimal.valueOf(2)).build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()
        ));

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.DUPLICATE_SKU_ITEM, ex.getErrorCode());
    }

    @Test
    @DisplayName("Đơn vị rời rạc (cái/bộ) nhưng số lượng là số thập phân -> Ném lỗi INVALID_UNIT_QUANTITY")
    void testCreateQuotation_InvalidUnitQuantity() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án F")
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.valueOf(2.5)) // Không được 2.5 cái
                                .build()
                ))
                .build();

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()
        ));
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(
                variantId1, CatalogSkuSummaryDto.builder()
                        .variantId(variantId1)
                        .sku("SKU-PUMP")
                        .unit("cái")
                        .status("ACTIVE")
                        .build()
        ));

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.INVALID_UNIT_QUANTITY, ex.getErrorCode());
    }

    @Test
    @DisplayName("validUntil trước ngày hôm nay -> Ném lỗi INVALID_VALID_UNTIL_DATE")
    void testCreateQuotation_InvalidValidUntil() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án G")
                .validUntil(LocalDate.now().minusDays(1)) // Hôm qua
                .build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.createQuotation(request));
        assertEquals(SalesErrorCode.INVALID_VALID_UNTIL_DATE, ex.getErrorCode());
    }

    // =========================================================================
    // NHÓM 2: PREVIEW CALCULATION, UPDATE, DELETE
    // =========================================================================

    @Test
    @DisplayName("previewCalculation: Tính toán trong RAM thành công, không mutate DB")
    void testPreviewCalculation_Success() {
        QuotationUpsertRequest request = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án Xem trước")
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.valueOf(200000))
                .items(List.of(
                        QuotationItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.valueOf(10))
                                .discountRate(BigDecimal.valueOf(15)) // Vượt hạn mức 10%
                                .build()
                ))
                .build();

        CatalogSkuSummaryDto skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .unit("cái")
                .sellingPrice(BigDecimal.valueOf(1000000))
                .costPrice(BigDecimal.valueOf(600000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, skuDto));

        StockSummaryDto stockDto = StockSummaryDto.builder()
                .variantId(variantId1)
                .availableQty(BigDecimal.valueOf(6))
                .build();
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, stockDto));

        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(
                CustomerSummaryDto.builder().customerId(customerId).customerName("Khách Xem Trước").status("ACTIVE").build()
        ));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        QuotationResponse response = quotationService.previewCalculation(request);

        assertNotNull(response);
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
        assertEquals(1, response.getItems().size());
        assertEquals(0, new BigDecimal("4.00").compareTo(response.getItems().get(0).getBackorderQty()));
        assertTrue(response.getItems().get(0).getIsBackorder());

        // requiresApproval phải là true vì chiết khấu 15% > 10%
        assertNotNull(response.getApproval());
        assertTrue(response.getApproval().getRequiresApproval());

        // Tuyệt đối không gọi save DB
        verify(quotationRepository, never()).save(any());
        verify(quotationItemRepository, never()).saveAll(anyList());
    }

    @Test
    @DisplayName("updateQuotation: Cập nhật thành công header bao gồm deliverySchedule và items mới")
    void testUpdateQuotation_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation existing = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("QT-202610-0001")
                .revisionNo(1)
                .status(QuotationStatus.DRAFT)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .createdBy(currentUserId)
                .customerId(customerId)
                .projectName("Dự án Cũ")
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existing));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);

        CustomerSummaryDto customerDto = CustomerSummaryDto.builder().customerId(customerId).customerName("Khách Hàng").status("ACTIVE").build();
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(customerDto));

        CatalogSkuSummaryDto skuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .unit("cái")
                .sellingPrice(BigDecimal.valueOf(2000000))
                .costPrice(BigDecimal.valueOf(1500000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, skuDto));
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Collections.emptyMap());
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        QuotationUpsertRequest updateReq = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án Mới Sau Cập Nhật")
                .deliveryAddress("Địa chỉ mới")
                .deliverySchedule("Giao 2 đợt trong tháng 11")
                .validUntil(LocalDate.now().plusDays(45))
                .vatRate(BigDecimal.valueOf(8))
                .shippingFee(BigDecimal.valueOf(300000))
                .items(List.of(
                        QuotationItemRequest.builder().variantId(variantId1).quantity(BigDecimal.valueOf(3)).discountRate(BigDecimal.ZERO).build()
                ))
                .build();

        QuotationResponse response = quotationService.updateQuotation(quotationId, updateReq);

        assertNotNull(response);
        assertEquals("Dự án Mới Sau Cập Nhật", response.getProjectName());
        assertEquals("Giao 2 đợt trong tháng 11", response.getDeliverySchedule());
        assertEquals("Địa chỉ mới", response.getDeliveryAddress());
        verify(quotationItemRepository, times(1)).deleteByQuotationId(quotationId);
        verify(quotationItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("updateQuotation: Nếu báo giá trước đó là REJECTED, sau khi sửa tự động chuyển về DRAFT")
    void testUpdateQuotation_WhenRejected_ResetsToDraft() {
        UUID quotationId = UUID.randomUUID();
        Quotation existing = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("QT-202610-0002")
                .revisionNo(1)
                .status(QuotationStatus.REJECTED)
                .quotationDate(LocalDate.now())
                .validUntil(LocalDate.now().plusDays(30))
                .createdBy(currentUserId)
                .customerId(customerId)
                .projectName("Dự án Bị Từ Chối")
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existing));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customerId).status("ACTIVE").build()));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        QuotationUpsertRequest updateReq = QuotationUpsertRequest.builder()
                .customerId(customerId)
                .projectName("Dự án Sửa Lại")
                .items(Collections.emptyList())
                .build();

        QuotationResponse response = quotationService.updateQuotation(quotationId, updateReq);

        assertNotNull(response);
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
    }

    @Test
    @DisplayName("updateQuotation: Trạng thái đã khóa (SENT) -> Ném lỗi QUOTATION_STATE_LOCKED")
    void testUpdateQuotation_LockedStatus_ThrowsException() {
        UUID quotationId = UUID.randomUUID();
        Quotation existing = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existing));

        QuotationUpsertRequest updateReq = QuotationUpsertRequest.builder().build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.updateQuotation(quotationId, updateReq));
        assertEquals(SalesErrorCode.QUOTATION_STATE_LOCKED, ex.getErrorCode());
    }

    @Test
    @DisplayName("updateQuotation: Người khác không có quyền sửa -> Ném lỗi FORBIDDEN_QUOTATION_ACCESS")
    void testUpdateQuotation_ForbiddenUser_ThrowsException() {
        UUID quotationId = UUID.randomUUID();
        Quotation existing = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .createdBy(UUID.randomUUID()) // Người khác tạo
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existing));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        QuotationUpsertRequest updateReq = QuotationUpsertRequest.builder().build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.updateQuotation(quotationId, updateReq));
        assertEquals(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("deleteQuotation: Xóa thành công bản nháp Rev 1")
    void testDeleteQuotation_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation existing = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .revisionNo(1)
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existing));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);

        quotationService.deleteQuotation(quotationId);

        verify(quotationItemRepository, times(1)).deleteByQuotationId(quotationId);
        verify(quotationRepository, times(1)).delete(existing);
    }

    @Test
    @DisplayName("deleteQuotation: Không cho phép xóa bản Rev 2 hoặc bản đã gửi -> Ném ONLY_DRAFT_CAN_BE_DELETED")
    void testDeleteQuotation_InvalidRevisionOrStatus() {
        UUID quotationId = UUID.randomUUID();
        Quotation existingRev2 = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .revisionNo(2) // Rev 2 không được xóa
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(existingRev2));

        AppException ex = assertThrows(AppException.class, () -> quotationService.deleteQuotation(quotationId));
        assertEquals(SalesErrorCode.ONLY_DRAFT_CAN_BE_DELETED, ex.getErrorCode());
    }

    // =========================================================================
    // NHÓM 3: SUBMIT, APPROVE, REJECT
    // =========================================================================

    @Test
    @DisplayName("submitForApproval: Trình duyệt thành công khi có chiết khấu vượt hạn mức và refresh tồn kho")
    void testSubmitForApproval_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .createdBy(currentUserId)
                .customerId(customerId)
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .quantity(BigDecimal.valueOf(10))
                .unitPrice(BigDecimal.valueOf(1000000))
                .discountRate(BigDecimal.valueOf(15)) // 15% vượt hạn mức 10%
                .unitCost(BigDecimal.valueOf(600000))
                .backorderQty(BigDecimal.ZERO)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(item));

        // Mock kho lúc này chỉ còn 4 cái -> refresh sẽ thành backorder = 6
        StockSummaryDto stockDto = StockSummaryDto.builder().variantId(variantId1).availableQty(BigDecimal.valueOf(4)).build();
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, stockDto));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        SubmitApprovalRequest req = SubmitApprovalRequest.builder()
                .approvalNote("Xin chiết khấu 15% cho khách sỉ 10 căn hộ")
                .build();

        QuotationResponse response = quotationService.submitForApproval(quotationId, req);

        assertNotNull(response);
        assertEquals(QuotationStatus.PENDING_APPROVAL, response.getStatus());
        assertEquals("Xin chiết khấu 15% cho khách sỉ 10 căn hộ", quotation.getApprovalNote());
        assertEquals(0, new BigDecimal("6.00").compareTo(item.getBackorderQty()));
        assertEquals(7, item.getLeadTimeDays());
        verify(quotationItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("submitForApproval: Chiết khấu nằm trong hạn mức (<=10%) -> Ném DISCOUNT_WITHIN_LIMIT")
    void testSubmitForApproval_DiscountWithinLimit() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .createdBy(currentUserId)
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId1)
                .quantity(BigDecimal.valueOf(2))
                .discountRate(BigDecimal.valueOf(8)) // 8% <= 10%
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(item));

        SubmitApprovalRequest req = SubmitApprovalRequest.builder().approvalNote("Xin duyệt").build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.submitForApproval(quotationId, req));
        assertEquals(SalesErrorCode.DISCOUNT_WITHIN_LIMIT, ex.getErrorCode());
    }

    @Test
    @DisplayName("submitForApproval: Không phải người tạo và không phải Admin -> Ném FORBIDDEN_QUOTATION_ACCESS")
    void testSubmitForApproval_ForbiddenUser() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.DRAFT)
                .createdBy(UUID.randomUUID()) // Người khác
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        SubmitApprovalRequest req = SubmitApprovalRequest.builder().approvalNote("Xin duyệt").build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.submitForApproval(quotationId, req));
        assertEquals(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("approveQuotation: Admin duyệt thành công, tính đúng marginPercentAtApproval")
    void testApproveQuotation_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .createdBy(UUID.randomUUID())
                .customerId(customerId)
                .approvalNote("Sales xin 15%")
                .build();

        // 10 cái, giá 1,000,000, giảm 10% -> NetRevenue = 9,000,000. Giá vốn 600,000 -> TotalCost = 6,000,000.
        // GrossProfit = 3,000,000. Margin = 3,000,000 / 9,000,000 * 100 = 33.33%
        QuotationItem item = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId1)
                .quantity(BigDecimal.valueOf(10))
                .unitPrice(BigDecimal.valueOf(1000000))
                .discountRate(BigDecimal.valueOf(10))
                .unitCost(BigDecimal.valueOf(600000))
                .build();

        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("owner").roles(List.of("SHOP_OWNER")).build());
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(item));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        ApproveQuotationRequest req = ApproveQuotationRequest.builder().approvalNote("Đồng ý duyệt").build();

        QuotationResponse response = quotationService.approveQuotation(quotationId, req);

        assertNotNull(response);
        assertEquals(QuotationStatus.APPROVED, quotation.getStatus());
        assertEquals(currentUserId, quotation.getApprovedBy());
        assertNotNull(quotation.getApprovedAt());
        assertEquals("Đồng ý duyệt", quotation.getApprovalNote());
        assertEquals(0, new BigDecimal("33.33").compareTo(quotation.getMarginPercentAtApproval()));
    }

    @Test
    @DisplayName("approveQuotation: Nhân viên thường không có quyền duyệt -> Ném ACCESS_DENIED")
    void testApproveQuotation_NotAdmin_ThrowsAccessDenied() {
        UUID quotationId = UUID.randomUUID();
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        AppException ex = assertThrows(AppException.class, () -> quotationService.approveQuotation(quotationId, null));
        assertEquals(CommonErrorCode.ACCESS_DENIED, ex.getErrorCode());
    }

    @Test
    @DisplayName("rejectQuotation: Admin từ chối thành công, lưu lý do vào approvalNote")
    void testRejectQuotation_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.PENDING_APPROVAL)
                .customerId(customerId)
                .build();

        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("owner").roles(List.of("SHOP_OWNER")).build());
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(Collections.emptyList());
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        RejectQuotationRequest req = RejectQuotationRequest.builder().approvalNote("Chiết khấu quá cao, tối đa chỉ cho 12%").build();

        QuotationResponse response = quotationService.rejectQuotation(quotationId, req);

        assertNotNull(response);
        assertEquals(QuotationStatus.REJECTED, quotation.getStatus());
        assertEquals("Chiết khấu quá cao, tối đa chỉ cho 12%", quotation.getApprovalNote());
        assertEquals(currentUserId, quotation.getApprovedBy());
    }

    // =========================================================================
    // NHÓM 5: CREATE REVISION, MARK ACCEPTED, MARK LOST
    // =========================================================================

    @Test
    @DisplayName("createRevision: Tạo Rev 2 thành công từ bản SENT, làm mới giá và tồn kho")
    void testCreateRevision_Success() {
        UUID sourceId = UUID.randomUUID();
        Quotation sourceQuotation = Quotation.builder()
                .quotationId(sourceId)
                .quotationCode("QT-202610-0010")
                .revisionNo(1)
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .customerId(customerId)
                .projectName("Dự án Bitexco")
                .deliverySchedule("Giao tháng 12")
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.valueOf(100000))
                .build();

        when(quotationRepository.findById(sourceId)).thenReturn(Optional.of(sourceQuotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.existsByQuotationCodeAndStatusIn(eq("QT-202610-0010"), anyList())).thenReturn(false);
        when(quotationRepository.findByQuotationCodeOrderByRevisionNoDesc("QT-202610-0010")).thenReturn(List.of(sourceQuotation));

        QuotationItem oldItem = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(sourceId)
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .quantity(BigDecimal.valueOf(5))
                .unitPrice(BigDecimal.valueOf(1000000)) // Giá cũ Rev 1
                .unitCost(BigDecimal.valueOf(600000))
                .discountRate(BigDecimal.valueOf(5))
                .sortOrder(1)
                .build();
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(sourceId)).thenReturn(List.of(oldItem));

        // Giá Catalog mới nhất tăng lên 1,200,000
        CatalogSkuSummaryDto newSkuDto = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .sku("SKU-PUMP-01")
                .unit("cái")
                .sellingPrice(BigDecimal.valueOf(1200000))
                .costPrice(BigDecimal.valueOf(750000))
                .status("ACTIVE")
                .build();
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(variantId1, newSkuDto));
        when(inventoryApi.getStockSummaries(Set.of(variantId1))).thenReturn(Collections.emptyMap());
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        QuotationResponse response = quotationService.createRevision(sourceId);

        assertNotNull(response);
        assertEquals(2, response.getRevisionNo());
        assertEquals("QT-202610-0010", response.getQuotationCode());
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
        assertEquals(sourceId, response.getParentQuotationId());
        assertEquals("Giao tháng 12", response.getDeliverySchedule());
        verify(quotationItemRepository, times(1)).saveAll(anyList());
    }

    @Test
    @DisplayName("createRevision: Tạo Rev 2 thành công từ bản LOST để tái đàm phán")
    void testCreateRevision_FromLost_Success() {
        UUID sourceId = UUID.randomUUID();
        Quotation sourceQuotation = Quotation.builder()
                .quotationId(sourceId)
                .quotationCode("QT-202610-0010")
                .revisionNo(1)
                .status(QuotationStatus.LOST)
                .createdBy(currentUserId)
                .customerId(customerId)
                .projectName("Dự án Bitexco")
                .deliverySchedule("Giao tháng 12")
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.valueOf(100000))
                .build();

        when(quotationRepository.findById(sourceId)).thenReturn(Optional.of(sourceQuotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.existsByQuotationCodeAndStatusIn(eq("QT-202610-0010"), anyList())).thenReturn(false);
        when(quotationRepository.findByQuotationCodeOrderByRevisionNoDesc("QT-202610-0010")).thenReturn(List.of(sourceQuotation));
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(sourceId)).thenReturn(Collections.emptyList());
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        QuotationResponse response = quotationService.createRevision(sourceId);

        assertNotNull(response);
        assertEquals(2, response.getRevisionNo());
        assertEquals("QT-202610-0010", response.getQuotationCode());
        assertEquals(QuotationStatus.DRAFT, response.getStatus());
        assertEquals(sourceId, response.getParentQuotationId());
    }

    @Test
    @DisplayName("createRevision: Bản nguồn không phải SENT -> Ném QUOTATION_NOT_SENT")
    void testCreateRevision_SourceNotSent_ThrowsException() {
        UUID sourceId = UUID.randomUUID();
        Quotation sourceQuotation = Quotation.builder()
                .quotationId(sourceId)
                .status(QuotationStatus.DRAFT) // Chưa gửi
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findById(sourceId)).thenReturn(Optional.of(sourceQuotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);

        AppException ex = assertThrows(AppException.class, () -> quotationService.createRevision(sourceId));
        assertEquals(SalesErrorCode.QUOTATION_NOT_SENT, ex.getErrorCode());
    }

    @Test
    @DisplayName("createRevision: Đã có Revision đang xử lý (DRAFT) -> Ném REVISION_ALREADY_EXISTS")
    void testCreateRevision_OpenRevisionExists_ThrowsException() {
        UUID sourceId = UUID.randomUUID();
        Quotation sourceQuotation = Quotation.builder()
                .quotationId(sourceId)
                .quotationCode("QT-DUPLICATE")
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findById(sourceId)).thenReturn(Optional.of(sourceQuotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.existsByQuotationCodeAndStatusIn(eq("QT-DUPLICATE"), anyList())).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> quotationService.createRevision(sourceId));
        assertEquals(SalesErrorCode.REVISION_ALREADY_EXISTS, ex.getErrorCode());
    }

    @Test
    @DisplayName("markAccepted: Chốt thành công từ bản SENT, đổi các bản cũ sang SUPERSEDED")
    void testMarkAccepted_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("QT-SUCCESS")
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .customerId(customerId)
                .build();

        Quotation oldRev1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode("QT-SUCCESS")
                .status(QuotationStatus.SENT)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.findByQuotationCodeAndQuotationIdNot("QT-SUCCESS", quotationId))
                .thenReturn(List.of(oldRev1));
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        MarkAcceptedRequest req = MarkAcceptedRequest.builder().signedFileUrl("https://files.com/contract.pdf").build();

        QuotationResponse response = quotationService.markAccepted(quotationId, req);

        assertNotNull(response);
        assertEquals(QuotationStatus.ACCEPTED, quotation.getStatus());
        assertNotNull(quotation.getAcceptedAt());
        assertEquals("https://files.com/contract.pdf", quotation.getSignedFileUrl());
        assertEquals(QuotationStatus.SUPERSEDED, oldRev1.getStatus());
        verify(quotationRepository, times(1)).saveAll(List.of(oldRev1));
    }

    @Test
    @DisplayName("markLost: Báo mất dự án thành công, lưu lostReason")
    void testMarkLost_Success() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .customerId(customerId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(quotationRepository.save(any(Quotation.class))).thenAnswer(i -> i.getArgument(0));

        MarkLostRequest req = MarkLostRequest.builder().lostReason("Giá cao hơn đối thủ 10%").build();

        QuotationResponse response = quotationService.markLost(quotationId, req);

        assertNotNull(response);
        assertEquals(QuotationStatus.LOST, quotation.getStatus());
        assertNotNull(quotation.getLostAt());
        assertEquals("Giá cao hơn đối thủ 10%", quotation.getLostReason());
    }

    @Test
    @DisplayName("markLost: Lý do mất để trống -> Ném LOST_REASON_REQUIRED")
    void testMarkLost_BlankReason_ThrowsException() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);

        MarkLostRequest req = MarkLostRequest.builder().lostReason("   ").build();

        AppException ex = assertThrows(AppException.class, () -> quotationService.markLost(quotationId, req));
        assertEquals(SalesErrorCode.LOST_REASON_REQUIRED, ex.getErrorCode());
    }

    // =========================================================================
    // NHÓM 6: TRA CỨU & DANH SÁCH BÁO GIÁ (QUERY & SEARCH TESTS)
    // =========================================================================

    @Test
    @DisplayName("getQuotationById: Admin xem chi tiết -> Hiển thị đầy đủ ownerData")
    void testGetQuotationById_Success_AdminSeesOwnerData() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("BG-202610-0001")
                .revisionNo(1)
                .status(QuotationStatus.PENDING_APPROVAL)
                .customerId(customerId)
                .projectName("Biệt thự Thảo Điền")
                .createdBy(UUID.randomUUID()) // Người khác tạo
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId1)
                .sku("SKU-001")
                .unit("cái")
                .quantity(BigDecimal.valueOf(2))
                .unitPrice(BigDecimal.valueOf(100_000))
                .unitCost(BigDecimal.valueOf(60_000))
                .discountRate(BigDecimal.ZERO)
                .sortOrder(1)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("owner").roles(List.of("SHOP_OWNER")).build());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(item));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerName("Nguyen Van A")
                .phone("0901234567")
                .status("ACTIVE")
                .build()));
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(
                variantId1, CatalogSkuSummaryDto.builder()
                        .variantId(variantId1)
                        .sku("SKU-001")
                        .variantName("Bơm nước cao cấp")
                        .sellingPrice(BigDecimal.valueOf(100_000))
                        .costPrice(BigDecimal.valueOf(60_000))
                        .unit("cái")
                        .status("ACTIVE")
                        .build()
        ));

        QuotationResponse response = quotationService.getQuotationById(quotationId);

        assertNotNull(response);
        assertEquals("BG-202610-0001", response.getQuotationCode());
        assertEquals("Nguyen Van A", response.getCustomerName());
        assertNotNull(response.getOwnerData(), "Admin phải xem được ownerData");
        assertNotNull(response.getOwnerData().getGrossProfit());
        assertNotNull(response.getOwnerData().getMarginPercent());
    }

    @Test
    @DisplayName("getQuotationById: Sales Staff xem báo giá của mình -> ownerData bị ẩn (null)")
    void testGetQuotationById_Success_StaffHidesOwnerData() {
        UUID quotationId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .quotationCode("BG-202610-0002")
                .revisionNo(1)
                .status(QuotationStatus.DRAFT)
                .customerId(customerId)
                .projectName("Nhà phố Sala")
                .createdBy(currentUserId) // Chính chủ tạo
                .vatRate(BigDecimal.valueOf(10))
                .shippingFee(BigDecimal.ZERO)
                .build();

        QuotationItem item = QuotationItem.builder()
                .quotationItemId(UUID.randomUUID())
                .quotationId(quotationId)
                .variantId(variantId1)
                .sku("SKU-001")
                .unit("cái")
                .quantity(BigDecimal.valueOf(1))
                .unitPrice(BigDecimal.valueOf(100_000))
                .unitCost(BigDecimal.valueOf(60_000))
                .discountRate(BigDecimal.ZERO)
                .sortOrder(1)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());
        when(quotationItemRepository.findByQuotationIdOrderBySortOrderAsc(quotationId)).thenReturn(List.of(item));
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.of(CustomerSummaryDto.builder()
                .customerId(customerId)
                .customerName("Nguyen Van B")
                .phone("0909876543")
                .status("ACTIVE")
                .build()));
        when(catalogApi.getSkuSummaries(Set.of(variantId1))).thenReturn(Map.of(
                variantId1, CatalogSkuSummaryDto.builder()
                        .variantId(variantId1)
                        .sku("SKU-001")
                        .variantName("Bơm nước cao cấp")
                        .sellingPrice(BigDecimal.valueOf(100_000))
                        .costPrice(BigDecimal.valueOf(60_000))
                        .unit("cái")
                        .status("ACTIVE")
                        .build()
        ));

        QuotationResponse response = quotationService.getQuotationById(quotationId);

        assertNotNull(response);
        assertEquals("BG-202610-0002", response.getQuotationCode());
        assertNull(response.getOwnerData(), "Sales Staff không được thấy ownerData");
    }

    @Test
    @DisplayName("getQuotationById: Sales Staff xem báo giá của người khác -> Ném FORBIDDEN_QUOTATION_ACCESS")
    void testGetQuotationById_Forbidden() {
        UUID quotationId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        Quotation quotation = Quotation.builder()
                .quotationId(quotationId)
                .createdBy(otherUserId)
                .build();

        when(quotationRepository.findById(quotationId)).thenReturn(Optional.of(quotation));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        AppException ex = assertThrows(AppException.class, () -> quotationService.getQuotationById(quotationId));
        assertEquals(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("getQuotationById: Không tìm thấy báo giá -> Ném QUOTATION_NOT_FOUND")
    void testGetQuotationById_NotFound() {
        UUID quotationId = UUID.randomUUID();
        when(quotationRepository.findById(quotationId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> quotationService.getQuotationById(quotationId));
        assertEquals(SalesErrorCode.QUOTATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getRevisionHistory: Trả về lịch sử phiên bản giảm dần theo revisionNo")
    void testGetRevisionHistory_Success() {
        String quotationCode = "BG-202610-0005";
        Quotation rev2 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(quotationCode)
                .revisionNo(2)
                .customerId(customerId)
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .build();
        Quotation rev1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(quotationCode)
                .revisionNo(1)
                .customerId(customerId)
                .status(QuotationStatus.SUPERSEDED)
                .createdBy(currentUserId)
                .build();

        when(quotationRepository.findByQuotationCodeOrderByRevisionNoDesc(quotationCode))
                .thenReturn(List.of(rev2, rev1));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());
        when(crmApi.getCustomerById(customerId))
                .thenReturn(Optional.of(CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .customerName("Tran Van C")
                        .phone("0911222333")
                        .status("ACTIVE")
                        .build()));
        when(iamApi.getUserById(currentUserId))
                .thenReturn(UserSummaryDto.builder()
                        .id(currentUserId)
                        .username("sales01")
                        .fullName("Sales Nguyen")
                        .email("sales@aqua.com")
                        .status("ACTIVE")
                        .build());

        List<QuotationListItemResponse> result = quotationService.getRevisionHistory(quotationCode);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(2, result.get(0).getRevisionNo());
        assertEquals(1, result.get(1).getRevisionNo());
        assertEquals("Tran Van C", result.get(0).getCustomerName());
        assertEquals("Sales Nguyen", result.get(0).getCreatedByName());
    }

    @Test
    @DisplayName("getRevisionHistory: Mã báo giá không tồn tại -> Ném QUOTATION_NOT_FOUND")
    void testGetRevisionHistory_NotFound() {
        String quotationCode = "BG-UNKNOWN";
        when(quotationRepository.findByQuotationCodeOrderByRevisionNoDesc(quotationCode))
                .thenReturn(Collections.emptyList());

        AppException ex = assertThrows(AppException.class, () -> quotationService.getRevisionHistory(quotationCode));
        assertEquals(SalesErrorCode.QUOTATION_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("getRevisionHistory: Nhân viên không sở hữu phiên bản nào và không phải Admin -> Ném FORBIDDEN_QUOTATION_ACCESS")
    void testGetRevisionHistory_Forbidden() {
        String quotationCode = "BG-202610-0008";
        UUID otherUserId = UUID.randomUUID();
        Quotation rev1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode(quotationCode)
                .revisionNo(1)
                .createdBy(otherUserId)
                .build();

        when(quotationRepository.findByQuotationCodeOrderByRevisionNoDesc(quotationCode))
                .thenReturn(List.of(rev1));
        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());

        AppException ex = assertThrows(AppException.class, () -> quotationService.getRevisionHistory(quotationCode));
        assertEquals(SalesErrorCode.FORBIDDEN_QUOTATION_ACCESS, ex.getErrorCode());
    }

    @Test
    @DisplayName("listQuotations: Admin tìm kiếm và phân trang nhận PageResponse")
    @SuppressWarnings("unchecked")
    void testListQuotations_AdminSuccess() {
        QuotationFilterRequest request = new QuotationFilterRequest();
        request.setPage(0);
        request.setSize(10);
        request.setSearch("Thảo Điền");
        request.setStatus(QuotationStatus.SENT);

        Quotation q1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode("BG-202610-0001")
                .revisionNo(1)
                .customerId(customerId)
                .projectName("Biệt thự Thảo Điền")
                .status(QuotationStatus.SENT)
                .createdBy(currentUserId)
                .totalAmount(BigDecimal.valueOf(15_000_000))
                .build();

        Page<Quotation> page = new PageImpl<>(List.of(q1), org.springframework.data.domain.PageRequest.of(0, 10), 1);

        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("owner").roles(List.of("SHOP_OWNER")).build());
        when(quotationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(crmApi.getCustomerById(customerId))
                .thenReturn(Optional.of(CustomerSummaryDto.builder()
                        .customerId(customerId)
                        .customerName("Nguyen Van A")
                        .phone("0901234567")
                        .status("ACTIVE")
                        .build()));
        when(iamApi.getUserById(currentUserId))
                .thenReturn(UserSummaryDto.builder()
                        .id(currentUserId)
                        .username("admin01")
                        .fullName("Admin Boss")
                        .email("admin@aqua.com")
                        .status("ACTIVE")
                        .build());

        PageResponse<QuotationListItemResponse> response = quotationService.listQuotations(request);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getTotalPages());
        assertEquals(0, response.getPage());
        assertEquals(10, response.getSize());
        assertEquals("BG-202610-0001", response.getItems().get(0).getQuotationCode());
        assertEquals("Nguyen Van A", response.getItems().get(0).getCustomerName());
        assertEquals("Admin Boss", response.getItems().get(0).getCreatedByName());
    }

    @Test
    @DisplayName("listQuotations: Nhân viên lọc danh sách với request null mặc định")
    @SuppressWarnings("unchecked")
    void testListQuotations_NullRequestHandledGracefully() {
        Quotation q1 = Quotation.builder()
                .quotationId(UUID.randomUUID())
                .quotationCode("BG-202610-0010")
                .revisionNo(1)
                .customerId(customerId)
                .status(QuotationStatus.DRAFT)
                .createdBy(currentUserId)
                .build();

        Page<Quotation> page = new PageImpl<>(List.of(q1), org.springframework.data.domain.PageRequest.of(0, 20), 1);

        when(iamApi.getCurrentUserId()).thenReturn(currentUserId);
        when(iamApi.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(currentUserId).username("staff").roles(List.of("SALES_STAFF")).build());
        when(quotationRepository.findAll(any(Specification.class), any(Pageable.class))).thenReturn(page);
        when(crmApi.getCustomerById(customerId)).thenReturn(Optional.empty());
        when(iamApi.getUserById(currentUserId)).thenReturn(null);

        PageResponse<QuotationListItemResponse> response = quotationService.listQuotations(null);

        assertNotNull(response);
        assertEquals(1, response.getItems().size());
        assertEquals("BG-202610-0010", response.getItems().get(0).getQuotationCode());
        assertNull(response.getItems().get(0).getCustomerName());
        assertNull(response.getItems().get(0).getCreatedByName());
    }
}
