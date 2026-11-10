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
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStage;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderStageRollbackRequest;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderDeliveryType;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStageStatus;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.enums.RollbackRequestStatus;
import com.aquasuite.ims.module.purchasing.internal.error.PurchasingErrorCode;
import com.aquasuite.ims.module.purchasing.internal.payload.request.*;
import com.aquasuite.ims.module.purchasing.internal.payload.response.LowStockSuggestionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.StageRollbackRequestResponse;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderItemRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderStageRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderStageRollbackRequestRepository;
import com.aquasuite.ims.module.purchasing.internal.service.PoCodeGenerator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderCalculator;
import com.aquasuite.ims.module.purchasing.internal.service.PurchaseOrderPdfService;
import com.aquasuite.ims.module.purchasing.internal.service.impl.PurchaseOrderServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderServiceTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @Mock
    private PurchaseOrderStageRepository stageRepository;

    @Mock
    private PurchaseOrderStageRollbackRequestRepository rollbackRequestRepository;

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
                stageRepository,
                rollbackRequestRepository,
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
                                .quantity(new BigDecimal("10.5"))
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
                                .quantity(new BigDecimal("15.75"))
                                .build()
                ))
                .build();

        PurchaseOrderResponse response = purchaseOrderService.createDraft(request);
        assertNotNull(response);
        assertEquals(new BigDecimal("15.75"), response.getItems().get(0).getQuantity());
    }

    @Test
    @DisplayName("Update Draft PO - Phase 2: Enter unit cost and expected date, transitions DRAFT -> QUOTED")
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
        assertEquals(PurchaseOrderStatus.QUOTED, response.getStatus());
        assertEquals(new BigDecimal("9500000.00"), response.getTotalAmount());
        assertEquals("Đã chốt giá qua Zalo, giao hàng trước 17h", response.getNote());
        assertEquals(1, response.getItems().size());
        assertEquals(new BigDecimal("950000.00"), response.getItems().get(0).getUnitCost());
        assertEquals(new BigDecimal("9500000.00"), response.getItems().get(0).getLineTotal());
    }

    @Test
    @DisplayName("Update PO - When CONFIRMED, throws PO_LOCKED_ITEMS_CANNOT_EDIT")
    void testUpdateDraft_LockedItems_WhenConfirmed_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .version(1)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        UpdatePurchaseOrderRequest request = UpdatePurchaseOrderRequest.builder()
                .version(1)
                .items(List.of(
                        UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(BigDecimal.ONE)
                                .build()
                ))
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.updateDraft(poId, request));
        assertEquals(PurchasingErrorCode.PO_LOCKED_ITEMS_CANNOT_EDIT.getCode(), ex.getErrorCode().getCode());
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
                .version(1)
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
    @DisplayName("Update PO with Staged delivery - Last stage balances remainder rounding")
    void testStagedDelivery_AmountBalance_Rounding() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.QUOTED)
                .version(0)
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(catalogApi.getSkuSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, skuDto1));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        // 3 items @ 1,000,000 = 3,000,000. Stages: 1,000,000 (33.33%), 1,000,000 (33.33%), last stage balances
        UpdatePurchaseOrderRequest request = UpdatePurchaseOrderRequest.builder()
                .version(0)
                .expectedDate(LocalDate.now().plusDays(10))
                .deliveryPlanType(PurchaseOrderDeliveryType.STAGED.name())
                .items(List.of(
                        UpdatePurchaseOrderRequest.UpdatePurchaseOrderItemRequest.builder()
                                .variantId(variantId1)
                                .quantity(new BigDecimal("3"))
                                .unitCost(new BigDecimal("1000000.00"))
                                .build()
                ))
                .stages(List.of(
                        CreatePurchaseOrderStageRequest.builder()
                                .stageName("Đợt 1")
                                .expectedDeliveryDate(LocalDate.now().plusDays(2))
                                .percent(new BigDecimal("33.33"))
                                .amount(new BigDecimal("1000000.00"))
                                .build(),
                        CreatePurchaseOrderStageRequest.builder()
                                .stageName("Đợt 2")
                                .expectedDeliveryDate(LocalDate.now().plusDays(5))
                                .percent(new BigDecimal("33.33"))
                                .amount(new BigDecimal("1000000.00"))
                                .build(),
                        CreatePurchaseOrderStageRequest.builder()
                                .stageName("Đợt 3")
                                .expectedDeliveryDate(LocalDate.now().plusDays(10))
                                .percent(new BigDecimal("33.34"))
                                .amount(new BigDecimal("999999.00")) // Discrepancy by 1 VND
                                .build()
                ))
                .build();

        PurchaseOrderResponse response = purchaseOrderService.updateDraft(poId, request);
        assertNotNull(response);
        assertEquals(3, response.getStages().size());
        // Last stage auto balanced to 1,000,000.00 so sum = 3,000,000.00
        assertEquals(new BigDecimal("1000000.00"), response.getStages().get(2).getAmount());
    }

    @Test
    @DisplayName("Confirm PO - Phase 3: Transition QUOTED -> CONFIRMED")
    void testConfirm_Success() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .poCode("PO-202611-0001")
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.QUOTED)
                .expectedDate(LocalDate.now().plusDays(7))
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
        assertEquals(1, response.getStages().size());
        assertEquals(new BigDecimal("5000000.00"), response.getStages().get(0).getAmount());
    }

    @Test
    @DisplayName("Confirm PO - Missing unit cost throws PO_CONFIRM_MISSING_PRICE")
    void testConfirm_MissingPrice_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .supplierId(supplierId)
                .status(PurchaseOrderStatus.QUOTED)
                .expectedDate(LocalDate.now().plusDays(5))
                .build();

        PurchaseOrderItem item = PurchaseOrderItem.builder()
                .variantId(variantId1)
                .sku("SKU-01")
                .quantityOrdered(BigDecimal.TEN)
                .unitCost(null)
                .build();
        po.addItem(item);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(supplierRepository.findById(supplierId)).thenReturn(Optional.of(activeSupplier));

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.confirmPurchaseOrder(poId));
        assertEquals(PurchasingErrorCode.PO_CONFIRM_MISSING_PRICE.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Settle Stage - Pay on delivery, settles stage and updates paid amount")
    void testSettleStage_Success() {
        UUID poId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("10000000.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        PurchaseOrderStage stage = PurchaseOrderStage.builder()
                .stageId(stageId)
                .purchaseOrder(po)
                .stageName("Đợt 1")
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage);

        PurchaseOrderStage stage2 = PurchaseOrderStage.builder()
                .stageId(UUID.randomUUID())
                .purchaseOrder(po)
                .stageName("Đợt 2")
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage2);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, poId)).thenReturn(Optional.of(stage));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        SettleStageRequest request = SettleStageRequest.builder()
                .actualAmount(new BigDecimal("5000000.00"))
                .paymentMethod("BANK_TRANSFER")
                .paymentReference("VCB123456")
                .note("Đã nhận hàng đủ và chuyển khoản")
                .build();

        PurchaseOrderResponse response = purchaseOrderService.settleStage(poId, stageId, request);

        assertNotNull(response);
        assertEquals(PurchaseOrderStatus.CONFIRMED, response.getStatus()); // Still 1 stage pending
        assertEquals(new BigDecimal("5000000.00"), response.getPaidAmount());
        assertEquals(PurchaseOrderStageStatus.SETTLED, stage.getStatus());
        assertEquals(new BigDecimal("5000000.00"), stage.getActualAmount());
        assertEquals("VCB123456", stage.getPaymentReference());
    }

    @Test
    @DisplayName("Settle Stage - Out of order settlement allowed (Stage 2 settled before Stage 1)")
    void testSettleStage_OutOfOrder_Success() {
        UUID poId = UUID.randomUUID();
        UUID stage1Id = UUID.randomUUID();
        UUID stage2Id = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("10000000.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        PurchaseOrderStage stage1 = PurchaseOrderStage.builder()
                .stageId(stage1Id)
                .purchaseOrder(po)
                .stageName("Đợt 1")
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        PurchaseOrderStage stage2 = PurchaseOrderStage.builder()
                .stageId(stage2Id)
                .purchaseOrder(po)
                .stageName("Đợt 2")
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage1);
        po.addStage(stage2);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stage2Id, poId)).thenReturn(Optional.of(stage2));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        SettleStageRequest request = SettleStageRequest.builder()
                .actualAmount(new BigDecimal("5000000.00"))
                .paymentMethod("CASH")
                .build();

        PurchaseOrderResponse response = purchaseOrderService.settleStage(poId, stage2Id, request);
        assertNotNull(response);
        assertEquals(PurchaseOrderStageStatus.SETTLED, stage2.getStatus());
        assertEquals(PurchaseOrderStageStatus.PENDING, stage1.getStatus());
    }

    @Test
    @DisplayName("Settle Stage - Different actual amount without note throws STAGE_SETTLEMENT_REASON_REQUIRED")
    void testSettleStage_DifferentAmount_MissingNote_Throws() {
        UUID poId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .build();

        PurchaseOrderStage stage = PurchaseOrderStage.builder()
                .stageId(stageId)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, poId)).thenReturn(Optional.of(stage));

        SettleStageRequest request = SettleStageRequest.builder()
                .actualAmount(new BigDecimal("4800000.00")) // Less than stage amount
                .paymentMethod("CASH")
                .note(null) // Missing required reason
                .build();

        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.settleStage(poId, stageId, request));
        assertEquals(PurchasingErrorCode.STAGE_SETTLEMENT_REASON_REQUIRED.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Settle All Stages - When last stage is settled, PO automatically transitions to COMPLETED")
    void testSettleAllStages_AutoCompleted() {
        UUID poId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("5000000.00"))
                .paidAmount(BigDecimal.ZERO)
                .build();

        PurchaseOrderStage stage = PurchaseOrderStage.builder()
                .stageId(stageId)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, poId)).thenReturn(Optional.of(stage));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        SettleStageRequest request = SettleStageRequest.builder()
                .actualAmount(new BigDecimal("5000000.00"))
                .paymentMethod("BANK_TRANSFER")
                .build();

        PurchaseOrderResponse response = purchaseOrderService.settleStage(poId, stageId, request);
        assertNotNull(response);
        assertEquals(PurchaseOrderStatus.COMPLETED, response.getStatus(), "PO must automatically complete when all stages are settled");
    }

    @Test
    @DisplayName("Rollback Workflow - Accountant requests rollback, Owner approves, stage returns to PENDING")
    void testRollbackWorkflow_AccountantRequest_OwnerApprove() {
        UUID poId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();
        UUID rollbackId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.COMPLETED)
                .totalAmount(new BigDecimal("5000000.00"))
                .paidAmount(new BigDecimal("5000000.00"))
                .build();

        PurchaseOrderStage stage = PurchaseOrderStage.builder()
                .stageId(stageId)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .actualAmount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.SETTLED)
                .build();
        po.addStage(stage);

        // 1. Accountant requests rollback
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, poId)).thenReturn(Optional.of(stage));
        when(rollbackRequestRepository.existsByStage_StageIdAndStatus(stageId, RollbackRequestStatus.PENDING)).thenReturn(false);
        when(rollbackRequestRepository.save(any(PurchaseOrderStageRollbackRequest.class))).thenAnswer(inv -> {
            PurchaseOrderStageRollbackRequest r = inv.getArgument(0);
            return r;
        });

        StageRollbackRequest req = StageRollbackRequest.builder().requestReason("Bấm nhầm số tiền").build();
        StageRollbackRequestResponse rollbackResp = purchaseOrderService.requestStageRollback(poId, stageId, req);
        assertNotNull(rollbackResp);
        assertEquals(RollbackRequestStatus.PENDING, rollbackResp.getStatus());

        // 2. Owner approves rollback
        PurchaseOrderStageRollbackRequest pendingRR = PurchaseOrderStageRollbackRequest.builder()
                .rollbackId(rollbackId)
                .stage(stage)
                .purchaseOrderId(poId)
                .status(RollbackRequestStatus.PENDING)
                .build();

        when(rollbackRequestRepository.findById(rollbackId)).thenReturn(Optional.of(pendingRR));
        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReviewRollbackRequest reviewReq = ReviewRollbackRequest.builder().approved(true).reviewNote("Đồng ý").build();
        PurchaseOrderResponse poResp = purchaseOrderService.reviewStageRollback(poId, rollbackId, reviewReq);

        assertNotNull(poResp);
        assertEquals(PurchaseOrderStatus.CONFIRMED, poResp.getStatus(), "PO must revert from COMPLETED to CONFIRMED");
        assertEquals(0, poResp.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(PurchaseOrderStageStatus.PENDING, stage.getStatus());
        assertNull(stage.getActualAmount());
    }

    @Test
    @DisplayName("Direct Rollback - Owner directly rolls back settled stage")
    void testDirectRollback_Owner_Success() {
        UUID poId = UUID.randomUUID();
        UUID stageId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("10000000.00"))
                .paidAmount(new BigDecimal("5000000.00"))
                .build();

        PurchaseOrderStage stage = PurchaseOrderStage.builder()
                .stageId(stageId)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .actualAmount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.SETTLED)
                .build();
        po.addStage(stage);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(stageRepository.findByStageIdAndPurchaseOrder_PurchaseOrderId(stageId, poId)).thenReturn(Optional.of(stage));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        StageRollbackRequest req = StageRollbackRequest.builder().requestReason("Chủ kiểm tra thấy sai").build();
        PurchaseOrderResponse resp = purchaseOrderService.directRollbackStage(poId, stageId, req);

        assertNotNull(resp);
        assertEquals(0, resp.getPaidAmount().compareTo(BigDecimal.ZERO));
        assertEquals(PurchaseOrderStageStatus.PENDING, stage.getStatus());
    }

    @Test
    @DisplayName("Reopen to Quoted - Allowed when 0 stages are settled")
    void testReopenToQuoted_Success() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .stages(new ArrayList<>(List.of(
                        PurchaseOrderStage.builder().status(PurchaseOrderStageStatus.PENDING).build()
                )))
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        ReopenPoRequest req = ReopenPoRequest.builder().reopenReason("NCC báo tăng giá 5%").build();
        PurchaseOrderResponse resp = purchaseOrderService.reopenToQuoted(poId, req);

        assertNotNull(resp);
        assertEquals(PurchaseOrderStatus.QUOTED, resp.getStatus());
        assertEquals("NCC báo tăng giá 5%", resp.getReopenReason());
    }

    @Test
    @DisplayName("Reopen to Quoted - With settled stages throws PO_CANNOT_REOPEN_WITH_SETTLED_STAGES")
    void testReopenToQuoted_WithSettledStages_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .stages(new ArrayList<>(List.of(
                        PurchaseOrderStage.builder().status(PurchaseOrderStageStatus.SETTLED).build()
                )))
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        ReopenPoRequest req = ReopenPoRequest.builder().reopenReason("Thử mở lại").build();
        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.reopenToQuoted(poId, req));
        assertEquals(PurchasingErrorCode.PO_CANNOT_REOPEN_WITH_SETTLED_STAGES.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Close Short PO - Allowed when >= 1 stage is settled; pending stages cancelled, PO COMPLETED")
    void testCloseShort_WithSettledStage_Success() {
        UUID poId = UUID.randomUUID();
        UUID stage1Id = UUID.randomUUID();
        UUID stage2Id = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("10000000.00"))
                .paidAmount(new BigDecimal("5000000.00"))
                .build();

        PurchaseOrderStage stage1 = PurchaseOrderStage.builder()
                .stageId(stage1Id)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.SETTLED)
                .build();
        PurchaseOrderStage stage2 = PurchaseOrderStage.builder()
                .stageId(stage2Id)
                .purchaseOrder(po)
                .amount(new BigDecimal("5000000.00"))
                .status(PurchaseOrderStageStatus.PENDING)
                .build();
        po.addStage(stage1);
        po.addStage(stage2);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));
        when(purchaseOrderRepository.save(any(PurchaseOrder.class))).thenAnswer(inv -> inv.getArgument(0));

        CloseShortPoRequest req = CloseShortPoRequest.builder().closeReason("NCC đứt hàng vĩnh viễn").build();
        PurchaseOrderResponse resp = purchaseOrderService.closeShortPurchaseOrder(poId, req);

        assertNotNull(resp);
        assertEquals(PurchaseOrderStatus.COMPLETED, resp.getStatus());
        assertTrue(resp.getIsShortClosed());
        assertEquals("NCC đứt hàng vĩnh viễn", resp.getCloseReason());
        assertEquals(PurchaseOrderStageStatus.CANCELLED, stage2.getStatus());
    }

    @Test
    @DisplayName("Close Short PO - No settled stages throws PO_CLOSE_SHORT_REQUIRES_SETTLED_STAGE")
    void testCloseShort_NoSettledStage_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .stages(new ArrayList<>(List.of(
                        PurchaseOrderStage.builder().status(PurchaseOrderStageStatus.PENDING).build()
                )))
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        CloseShortPoRequest req = CloseShortPoRequest.builder().closeReason("Chưa nhận đợt nào").build();
        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.closeShortPurchaseOrder(poId, req));
        assertEquals(PurchasingErrorCode.PO_CLOSE_SHORT_REQUIRES_SETTLED_STAGE.getCode(), ex.getErrorCode().getCode());
    }

    @Test
    @DisplayName("Cancel PO - With settled stages throws PO_CANNOT_CANCEL_WITH_SETTLED_STAGES")
    void testCancel_WithSettledStages_Throws() {
        UUID poId = UUID.randomUUID();
        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .stages(new ArrayList<>(List.of(
                        PurchaseOrderStage.builder().status(PurchaseOrderStageStatus.SETTLED).build()
                )))
                .build();

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        CancelPurchaseOrderRequest req = CancelPurchaseOrderRequest.builder().cancelReason("Muốn hủy").build();
        AppException ex = assertThrows(AppException.class, () -> purchaseOrderService.cancelPurchaseOrder(poId, req));
        assertEquals(PurchasingErrorCode.PO_CANNOT_CANCEL_WITH_SETTLED_STAGES.getCode(), ex.getErrorCode().getCode());
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

        StockSummaryDto stock = StockSummaryDto.builder()
                .variantId(variantId1)
                .availableQty(new BigDecimal("5"))
                .minimumQty(new BigDecimal("10"))
                .maximumQty(new BigDecimal("30"))
                .onHandQty(new BigDecimal("5"))
                .build();
        when(inventoryApi.getStockSummaries(List.of(variantId1))).thenReturn(Map.of(variantId1, stock));
        when(purchasingApi.getIncomingStockQtyMap(List.of(variantId1))).thenReturn(Map.of(variantId1, new BigDecimal("10")));

        List<LowStockSuggestionResponse> suggestions = purchaseOrderService.getLowStockSuggestions(supplierId);

        assertEquals(1, suggestions.size());
        LowStockSuggestionResponse s = suggestions.get(0);
        assertEquals(variantId1, s.getVariantId());
        assertEquals(new BigDecimal("15"), s.getSuggestedQty());
        assertEquals(new BigDecimal("900000.00"), s.getLastSupplyPrice());
    }
}
