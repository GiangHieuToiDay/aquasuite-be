package com.aquasuite.ims.module.purchasing;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.internal.entity.Supplier;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProduct;
import com.aquasuite.ims.module.inventory.internal.entity.SupplierProductId;
import com.aquasuite.ims.module.inventory.internal.enums.SupplierStatus;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierProductRepository;
import com.aquasuite.ims.module.inventory.internal.repository.SupplierRepository;
import com.aquasuite.ims.module.purchasing.api.PurchasingApi;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrder;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderItem;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.error.PurchasingErrorCode;
import com.aquasuite.ims.module.purchasing.internal.payload.request.*;
import com.aquasuite.ims.module.purchasing.internal.payload.response.LowStockSuggestionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierItemOptionResponse;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderItemRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.module.purchasing.internal.service.PoCodeGenerator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderCalculator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderPdfService;
import com.aquasuite.ims.module.purchasing.internal.service.impl.PurchaseOrderServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private SupplierRepository supplierRepository;

    @Mock
    private SupplierProductRepository supplierProductRepository;

    @Mock
    private CatalogApi catalogApi;

    @Mock
    private InventoryApi inventoryApi;

    @Mock
    private PurchasingApi purchasingApi;

    @Mock
    private IamApi iamApi;

    @Mock
    private PoCodeGenerator poCodeGenerator;

    private PurchaseOrderCalculator calculator;
    private PurchaseOrderPdfService pdfService;
    private PurchaseOrderServiceImpl purchaseOrderService;

    private UUID supplierId;
    private UUID variantId1;
    private UUID variantId2;
    private UUID productId1;
    private UUID userId;

    private Supplier activeSupplier;
    private CatalogSkuSummaryDto skuDto1;
    private CatalogSkuSummaryDto skuDto2;

    @BeforeEach
    void setUp() {
        calculator = new PurchaseOrderCalculator();
        pdfService = new PurchaseOrderPdfService();
        purchaseOrderService = new PurchaseOrderServiceImpl(
                purchaseOrderRepository,
                purchaseOrderItemRepository,
                supplierRepository,
                supplierProductRepository,
                catalogApi,
                inventoryApi,
                purchasingApi,
                iamApi,
                poCodeGenerator,
                calculator,
                pdfService
        );

        supplierId = UUID.randomUUID();
        variantId1 = UUID.randomUUID();
        variantId2 = UUID.randomUUID();
        productId1 = UUID.randomUUID();
        userId = UUID.randomUUID();

        activeSupplier = Supplier.builder()
                .supplierId(supplierId)
                .supplierCode("SUP-001")
                .supplierName("Inax LIXIL Vietnam")
                .phone("0912345678")
                .status(SupplierStatus.ACTIVE)
                .build();

        skuDto1 = CatalogSkuSummaryDto.builder()
                .variantId(variantId1)
                .productId(productId1)
                .sku("SKU-INAX-01")
                .variantName("Vòi lavabo Inax LFV-1112S")
                .productName("Vòi lavabo nóng lạnh")
                .unit("cái")
                .sellingPrice(new BigDecimal("1500000"))
                .costPrice(new BigDecimal("1000000"))
                .status("ACTIVE")
                .build();

        skuDto2 = CatalogSkuSummaryDto.builder()
                .variantId(variantId2)
                .productId(productId1)
                .sku("SKU-PIPE-MET")
                .variantName("Ống đồng dẫn nước")
                .productName("Ống đồng")
                .unit("mét")
                .sellingPrice(new BigDecimal("200000"))
                .costPrice(new BigDecimal("120000"))
                .status("ACTIVE")
                .build();

        lenient().when(iamApi.getCurrentUserId()).thenReturn(userId);
        lenient().when(iamApi.getUserById(userId)).thenReturn(
                UserSummaryDto.builder().id(userId).fullName("Shop Owner").build()
        );
    }

    @Test
    @DisplayName("Create Draft PO - Phase 1: Total amount is 0, unit cost is null, status is DRAFT")
    void testCreateDraft_Success() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(catalogApi.getSkuSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, skuDto1));
        when(poCodeGenerator.generatePoCode()).thenReturn("PO-202611-0001");
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatePurchaseOrderRequest request = CreatePurchaseOrderRequest.builder()
                .supplierId(supplierId)
                .warehouseCode("WH_MAIN")
                .items(List.of(
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(new BigDecimal("10"))
                                .build()
                ))
                .build();

        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);

        assertNotNull(response);
        assertEquals("PO-202611-0001", response.getPoCode());
        assertEquals(PurchaseOrderStatus.DRAFT, response.getStatus());
        assertEquals(BigDecimal.ZERO, response.getTotalAmount());
        assertEquals(1, response.getItems().size());

        var item = response.getItems().get(0);
        assertEquals(new BigDecimal("10"), item.getQuantity());
        assertNull(item.getUnitCost(), "Phase 1 create form must not have unit cost");
        assertNull(item.getLineTotal());
    }

    @Test
    @DisplayName("Create Draft PO - Supplier not active should throw SUPPLIER_NOT_ACTIVE")
    void testCreateDraft_SupplierNotActive_Throws() {
        Supplier inactiveSupplier = Supplier.builder()
                .supplierId(supplierId)
                .supplierName("Inactive NCC")
                .status(SupplierStatus.INACTIVE)
                .build();
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(inactiveSupplier));

        CreatePurchaseOrderRequest request = CreatePurchaseOrderRequest.builder()
                .supplierId(supplierId)
                .items(List.of(
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.TEN)
                                .build()
                ))
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.createDraft(request));
        assertEquals(PurchasingErrorCode.SUPPLIER_NOT_ACTIVE.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Create Draft PO - Duplicate SKU in request should throw DUPLICATE_SKU_IN_PO")
    void testCreateDraft_DuplicateSku_Throws() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));

        CreatePurchaseOrderRequest request = CreatePurchaseOrderRequest.builder()
                .supplierId(supplierId)
                .items(List.of(
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.ONE)
                                .build(),
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.TEN)
                                .build()
                ))
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.createDraft(request));
        assertEquals(PurchasingErrorCode.DUPLICATE_SKU_IN_PO.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Create Draft PO - Discrete unit with decimals should throw INVALID_QUANTITY")
    void testCreateDraft_InvalidQuantity_DiscreteUnit_Throws() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(catalogApi.getSkuSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, skuDto1));

        CreatePurchaseOrderRequest request = CreatePurchaseOrderRequest.builder()
                .supplierId(supplierId)
                .items(List.of(
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(new BigDecimal("10.5")) // 'cái' cannot have decimals
                                .build()
                ))
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.createDraft(request));
        assertEquals(PurchasingErrorCode.INVALID_QUANTITY.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Create Draft PO - Continuous unit (mét) allows up to 2 decimal places")
    void testCreateDraft_ValidQuantity_ContinuousUnit_Success() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(catalogApi.getSkuSummaries(List.of(variantId2))).thenReturn(Map.of(variantId2, skuDto2));
        when(poCodeGenerator.generatePoCode()).thenReturn("PO-202611-0002");
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CreatePurchaseOrderRequest request = CreatePurchaseOrderRequest.builder()
                .supplierId(supplierId)
                .items(List.of(
                        CreatePurchaseOrderRequest.CreatePurchaseOrderItemRequest.builder()
                                .variantId(variantId2)
                                .quantity(new BigDecimal("15.75")) // 'mét' allows 2 decimals
                                .build()
                ))
                .build();

        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);
        assertNotNull(response);
        assertEquals(new BigDecimal("15.75"), response.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Update Draft PO - Phase 2: Enter unit cost and expected date, calculate total amount")
    void testUpdateDraft_Success() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .poCode("PO-202611-0001")
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.DRAFT)
                .version(0)
                .orderDate(LocalDate.now())
                .totalAmount(BigDecimal.ZERO)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(catalogApi.getSkuSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, skuDto1));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        UpdatePurchaseOrderRequest request = UpdatePurchaseOrderRequest.builder()
                .version(0)
                .expectedDate(LocalDate.now().plusDays(7))
                .note("Đã chốt giá qua Zalo, giao hàng trước 17h")
                .items(List.of(
                        UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(new BigDecimal("10"))
                                .unitCost(new BigDecimal("950000.00"))
                                .build()
                ))
                .build();

        PurchaseOrderResponse response = purchaseOrderService.updateDraft(poId, request);

        assertNotNull(response);
        assertEquals(new BigDecimal("9500000.00"), response.getTotalAmount());
        assertEquals("Đã chốt giá qua Zalo, giao hàng trước 17h", response.getNote());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("950000.00"), response.getItems().get(0).getUnitCost());
        assertEquals(new BigDecimal("9500000.00"), response.getItems().get(0).getLineTotal());
    }

    @Test
    @DisplayName("Update PO - Optimistic lock conflict throws PO_VERSION_CONFLICT")
    void testUpdateDraft_VersionConflict_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.DRAFT)
                .version(2)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        UpdatePurchaseOrderRequest request = UpdatePurchaseOrderRequest.builder()
                .version(1) // version mismatch
                .items(List.of(
                        UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.updateDraft(poId, request));
        assertEquals(PurchasingErrorCode.PO_VERSION_CONFLICT.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Confirm PO - Phase 3: Transition DRAFT -> CONFIRMED, all items have prices")
    void testConfirm_Success() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .poCode("PO-202611-0001")
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .purchaseOrderItemId(UUID.randomUUID())
                .purchaseOrder(po)
                .variantId(variantId1)
                .sku("SKU-01")
                .quantityOrdered(new BigDecimal("10"))
                .unitCost(new BigDecimal("500000.00"))
                .build();
        po.addItem(item);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));
        when(catalogApi.getSkuSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, skuDto1));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        PurchaseOrderResponse response = purchaseOrderService.confirmPurchaseOrder(poId);

        assertNotNull(response);
        assertEquals(PurchaseOrderStatus.CONFIRMED, response.getStatus());
        assertEquals(new BigDecimal("5000000.00"), response.getTotalAmount());
        assertEquals(userId, response.getConfirmedBy());
        assertNotNull(response.getConfirmedAt());
    }

    @Test
    @DisplayName("Confirm PO - Missing unit cost throws PO_CONFIRM_MISSING_PRICE")
    void testConfirm_MissingPrice_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.DRAFT)
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .variantId(variantId1)
                .sku("SKU-01")
                .quantityOrdered(BigDecimal.TEN)
                .unitCost(null) // Missing price
                .build();
        po.addItem(item);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.confirmPurchaseOrder(poId));
        assertEquals(PurchasingErrorCode.PO_CONFIRM_MISSING_PRICE.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cancel PO - Confirmed with existing goods receipt throws PO_CANNOT_CANCEL_RECEIPT_EXISTS")
    void testCancel_Confirmed_ReceiptExists_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(inventoryApi.hasConfirmedGoodsReceipt(poId)).thenReturn(true);

        CancelPurchaseOrderRequest request = CancelPurchaseOrderRequest.builder()
                .cancelReason("Nhà cung cấp hủy giao hàng")
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.cancelPurchaseOrder(poId, request));
        assertEquals(PurchasingErrorCode.PO_CANNOT_CANCEL_RECEIPT_EXISTS.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Close PO - Status must be PARTIALLY_RECEIVED, closed with reason")
    void testClose_PartiallyReceived_Success() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(inventoryApi.hasDraftGoodsReceipt(poId)).thenReturn(false);
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ClosePurchaseOrderRequest request = ClosePurchaseOrderRequest.builder()
                .closeReason("Nhà cung cấp hết hàng đợt 2, đồng ý chốt nhận thiếu")
                .build();

        PurchaseOrderResponse response = purchaseOrderService.closePurchaseOrder(poId, request);

        assertEquals(PurchaseOrderStatus.COMPLETED, response.getStatus());
        assertEquals("Nhà cung cấp hết hàng đợt 2, đồng ý chốt nhận thiếu", response.getCloseReason());
        assertEquals(userId, response.getClosedBy());
    }

    @Test
    @DisplayName("Low-Stock Suggestions - Auto-Fill algorithm: maxStock - availableStock - incomingStock")
    void testGetLowStockSuggestions_Success() {
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));

        SupplierProduct sp = SupplierProduct.builder()
                .id(SupplierProductId.builder().supplierId(supplierId).productId(productId1).build())
                .lastSupplyPrice(new BigDecimal("900000.00"))
                .build();
        when(supplierProductRepository.findByIdSupplierId(supplierId)).thenReturn(List.of(sp));
        when(catalogApi.getSkusByProductIds(List.of(productId1))).thenReturn(List.of(skuDto1));

        // Available = 5, Min = 10, Max = 30 -> Low stock!
        StockSummaryDto stock = StockSummaryDto.builder()
                .variantId(variantId1)
                .availableQty(new BigDecimal("5"))
                .minimumQty(new BigDecimal("10"))
                .maximumQty(new BigDecimal("30"))
                .onHandQty(new BigDecimal("5"))
                .build();
        when(inventoryApi.getStockSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, stock));
        // Incoming in open POs = 10
        when(purchasingApi.getIncomingStockQtyMap(List.of(variantId1))).thenReturn(Map.of(variantId1, new BigDecimal("10")));

        List<LowStockSuggestionResponse> suggestions = purchaseOrderService.getLowStockSuggestions(supplierId);

        assertEquals(1, suggestions.size());
        LowStockSuggestionResponse s = suggestions.get(0);
        assertEquals(variantId1, s.getVariantId());
        // Proposed = 30 (max) - 5 (avail) - 10 (incoming) = 15
        assertEquals(new BigDecimal("15"), s.getSuggestedQty());
        assertEquals(new BigDecimal("900000.00"), s.getLastSupplyPrice());
    }
}