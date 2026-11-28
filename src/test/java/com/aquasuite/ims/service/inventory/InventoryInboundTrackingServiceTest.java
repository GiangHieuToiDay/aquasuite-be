package com.aquasuite.ims.service.inventory;

import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.dto.inventory.response.InventoryInboundKpiResponse;
import com.aquasuite.ims.dto.inventory.response.ReadableStockMovementResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundSummaryResponse;
import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderBriefView;
import com.aquasuite.ims.dto.purchasing.response.VariantPurchaseOrderLineView;
import com.aquasuite.ims.entity.inventory.GoodsReceipt;
import com.aquasuite.ims.entity.inventory.GoodsReceiptItem;
import com.aquasuite.ims.entity.inventory.Inventory;
import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.enums.inventory.MovementType;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.inventory.InventoryErrorCode;
import com.aquasuite.ims.repository.inventory.GoodsReceiptItemRepository;
import com.aquasuite.ims.repository.inventory.GoodsReceiptRepository;
import com.aquasuite.ims.repository.inventory.InventoryRepository;
import com.aquasuite.ims.repository.inventory.StockMovementRepository;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.impl.InventoryInboundTrackingServiceImpl;
import com.aquasuite.ims.service.purchasing.PurchaseOrderTrackingQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InventoryInboundTrackingServiceTest {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private PurchaseOrderTrackingQueryService poTrackingQueryService;
    @Mock private SalesQueryService salesQueryService;
    @Mock private IamQueryService iamApi;

    private InventoryInboundTrackingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InventoryInboundTrackingServiceImpl(
                inventoryRepository,
                goodsReceiptRepository,
                goodsReceiptItemRepository,
                stockMovementRepository,
                poTrackingQueryService,
                salesQueryService,
                iamApi,
                new ObjectMapper());
    }

    @Test
    @DisplayName("AC-01: KPI tổng quan tính chính xác SKU cần đặt thêm, SKU đang về, PO trễ hẹn và hàng đang giữ")
    void getKpiOverview_calculatesAccurateCounts() {
        UUID vNeedOrder = UUID.randomUUID();
        UUID vCovered = UUID.randomUUID();
        UUID vHealthy = UUID.randomUUID();
        UUID poOverdueId = UUID.randomUUID();

        Inventory invNeedOrder = Inventory.builder()
                .inventoryId(UUID.randomUUID()).variantId(vNeedOrder).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("115")).reservedQty(new BigDecimal("4")).minimumQty(new BigDecimal("130"))
                .build();
        Inventory invCovered = Inventory.builder()
                .inventoryId(UUID.randomUUID()).variantId(vCovered).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("136")).reservedQty(new BigDecimal("8")).minimumQty(new BigDecimal("130"))
                .build();
        Inventory invHealthy = Inventory.builder()
                .inventoryId(UUID.randomUUID()).variantId(vHealthy).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("102")).reservedQty(BigDecimal.ZERO).minimumQty(BigDecimal.ZERO)
                .build();

        when(inventoryRepository.findAll()).thenReturn(List.of(invNeedOrder, invCovered, invHealthy));
        when(poTrackingQueryService.findAllOpenLines()).thenReturn(List.of(
                VariantPurchaseOrderLineView.builder()
                        .purchaseOrderId(poOverdueId)
                        .variantId(vCovered)
                        .status("PARTIALLY_RECEIVED")
                        .orderedQty(new BigDecimal("26"))
                        .receivedQty(new BigDecimal("6"))
                        .remainingQty(new BigDecimal("20"))
                        .expectedDate(LocalDate.now().minusDays(1))
                        .build()
        ));

        InventoryInboundKpiResponse kpi = service.getKpiOverview();

        assertEquals(3L, kpi.totalSkus());
        assertEquals(1L, kpi.needOrderCount()); // chỉ vNeedOrder cần đặt thêm; vCovered (128 + 20 = 148 > 130) đã đủ hàng về
        assertEquals(1L, kpi.incomingSkuCount());
        assertEquals(new BigDecimal("20"), kpi.totalIncomingQty());
        assertEquals(1L, kpi.openPoCount());
        assertEquals(1L, kpi.overduePoCount());
        assertEquals(2L, kpi.reservedSkuCount());
        assertEquals(new BigDecimal("12"), kpi.totalReservedQty());
    }

    @Test
    @DisplayName("AC-02: Summaries phân biệt đúng NEED_ORDER và INCOMING_COVERED")
    void getInboundSummaries_distinguishesNeedOrderAndCovered() {
        UUID v1 = UUID.randomUUID();
        UUID v2 = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        LocalDate expected = LocalDate.now().plusDays(2);

        Inventory inv1 = Inventory.builder()
                .variantId(v1).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("115")).reservedQty(new BigDecimal("4")).minimumQty(new BigDecimal("130"))
                .build();
        Inventory inv2 = Inventory.builder()
                .variantId(v2).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("136")).reservedQty(new BigDecimal("8")).minimumQty(new BigDecimal("130"))
                .build();

        when(inventoryRepository.findByVariantIdInAndWarehouseCode(any(), eq("WH_MAIN")))
                .thenReturn(List.of(inv1, inv2));
        when(poTrackingQueryService.findLinesByVariantIds(any(), eq(false))).thenReturn(List.of(
                VariantPurchaseOrderLineView.builder()
                        .purchaseOrderId(poId)
                        .variantId(v2)
                        .status("PARTIALLY_RECEIVED")
                        .remainingQty(new BigDecimal("20"))
                        .expectedDate(expected)
                        .build()
        ));

        Map<UUID, VariantInboundSummaryResponse> summaries = service.getInboundSummaries(List.of(v1, v2));

        VariantInboundSummaryResponse s1 = summaries.get(v1);
        assertEquals("NEED_ORDER", s1.restockActionHint());
        assertEquals(new BigDecimal("19"), s1.suggestedOrderQty());
        assertEquals(0, s1.openPoCount());

        VariantInboundSummaryResponse s2 = summaries.get(v2);
        assertEquals("INCOMING_COVERED", s2.restockActionHint());
        assertEquals(new BigDecimal("20"), s2.incomingQty());
        assertEquals(1, s2.openPoCount());
        assertEquals(expected, s2.nearestExpectedDate());
        assertFalse(s2.hasOverduePo());
    }

    @Test
    @DisplayName("AC-03: Chi tiết PO của SKU trả đúng tiến độ nhận và các phiếu GRN gắn với SKU")
    void getVariantPurchaseOrders_returnsProgressAndMatchingReceipts() {
        UUID variantId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        UUID grId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();

        Inventory inv = Inventory.builder()
                .inventoryId(UUID.randomUUID()).variantId(variantId).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("136")).reservedQty(new BigDecimal("8")).minimumQty(new BigDecimal("130"))
                .build();

        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(inv));
        when(poTrackingQueryService.findLinesByVariantIds(List.of(variantId), false)).thenReturn(List.of(
                VariantPurchaseOrderLineView.builder()
                        .purchaseOrderId(poId)
                        .purchaseOrderItemId(UUID.randomUUID())
                        .poCode("PO-2611-004")
                        .status("PARTIALLY_RECEIVED")
                        .statusLabel("Nhận một phần")
                        .supplierName("TOTO Việt Nam")
                        .unit("Bộ")
                        .orderedQty(new BigDecimal("26"))
                        .receivedQty(new BigDecimal("6"))
                        .remainingQty(new BigDecimal("20"))
                        .expectedDate(LocalDate.now().plusDays(1))
                        .build()
        ));

        GoodsReceipt gr = GoodsReceipt.builder()
                .goodsReceiptId(grId).receiptCode("GRN26112613068363").purchaseOrderId(poId)
                .status("CONFIRMED").receiptDate(LocalDate.now()).confirmedBy(actorId)
                .confirmedAt(OffsetDateTime.now())
                .build();
        when(goodsReceiptRepository.findByPurchaseOrderIdInOrderByCreatedAtDesc(Set.of(poId)))
                .thenReturn(List.of(gr));
        when(goodsReceiptItemRepository.findByGoodsReceiptIdInAndVariantId(Set.of(grId), variantId))
                .thenReturn(List.of(GoodsReceiptItem.builder()
                        .goodsReceiptId(grId).variantId(variantId).quantity(new BigDecimal("6")).build()));
        when(iamApi.getUserById(actorId)).thenReturn(
                UserSummaryDto.builder().id(actorId).fullName("Phạm Văn Nam").build());

        VariantInboundDetailResponse detail = service.getVariantPurchaseOrders(variantId, false);

        assertEquals(new BigDecimal("128"), detail.availableQty());
        assertEquals(new BigDecimal("20"), detail.incomingQty());
        assertEquals(new BigDecimal("148"), detail.projectedAvailableQty());
        assertEquals(1, detail.purchaseOrders().size());

        var poItem = detail.purchaseOrders().get(0);
        assertEquals("PO-2611-004", poItem.poCode());
        assertEquals(23, poItem.progressPercent());
        assertEquals(1, poItem.receipts().size());
        assertEquals("GRN26112613068363", poItem.receipts().get(0).receiptCode());
        assertEquals(new BigDecimal("6"), poItem.receipts().get(0).receivedQty());
        assertEquals("Phạm Văn Nam", poItem.receipts().get(0).confirmedByName());
    }

    @Test
    @DisplayName("AC-03: Trả 404 INV_001 khi SKU chưa có bản ghi tồn kho")
    void getVariantPurchaseOrders_notFoundThrowsInv001() {
        UUID missingVariant = UUID.randomUUID();
        when(inventoryRepository.findByVariantId(missingVariant)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> service.getVariantPurchaseOrders(missingVariant, false));
        assertEquals(InventoryErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-04: Thẻ kho tường minh ẩn sạch JSON nội bộ của STOCK_DISPATCH và gắn PO/NCC cho GOODS_RECEIPT")
    void getReadableMovements_sanitizesDispatchJsonAndEnrichesGoodsReceipt() {
        UUID variantId = UUID.randomUUID();
        UUID poId = UUID.randomUUID();
        UUID grId = UUID.randomUUID();
        UUID orderId = UUID.fromString("10000000-0000-4000-8000-000000000111");
        UUID deliveryId = UUID.fromString("5a850cf4-2d71-4000-8000-000000000222");

        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(
                Inventory.builder().inventoryId(UUID.randomUUID()).variantId(variantId).build()));

        StockMovement grMove = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .variantId(variantId)
                .movementType(MovementType.GOODS_RECEIPT)
                .quantity(new BigDecimal("5"))
                .balanceAfter(new BigDecimal("135"))
                .referenceType("GOODS_RECEIPT")
                .referenceId("GRN26112613068363")
                .note("Nhập kho theo phiếu [GRN26112613068363]")
                .createdAt(Instant.now())
                .build();

        String rawDispatchJson = String.format(
                "{\"operationId\":\"0792506e-3f2f-4852-9c37-5a850cf42d71\",\"deliveryId\":\"%s\",\"orderId\":\"%s\",\"quantity\":1}",
                deliveryId, orderId);
        StockMovement dispatchMove = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .variantId(variantId)
                .movementType(MovementType.STOCK_DISPATCH)
                .quantity(new BigDecimal("-1"))
                .balanceAfter(new BigDecimal("130"))
                .referenceType("DELIVERY")
                .referenceId(deliveryId.toString())
                .note(rawDispatchJson)
                .createdAt(Instant.now().minusSeconds(60))
                .build();

        when(stockMovementRepository.findByVariantIdOrderByCreatedAtDesc(eq(variantId), any()))
                .thenReturn(new PageImpl<>(List.of(grMove, dispatchMove), PageRequest.of(0, 20), 2));

        when(goodsReceiptRepository.findByReceiptCodeIn(Set.of("GRN26112613068363"))).thenReturn(List.of(
                GoodsReceipt.builder()
                        .goodsReceiptId(grId)
                        .receiptCode("GRN26112613068363")
                        .purchaseOrderId(poId)
                        .build()));

        when(poTrackingQueryService.findOrderBriefsByIds(Set.of(poId))).thenReturn(Map.of(
                poId, PurchaseOrderBriefView.builder()
                        .purchaseOrderId(poId)
                        .poCode("PO-2611-004")
                        .status("PARTIALLY_RECEIVED")
                        .supplierName("TOTO Việt Nam")
                        .build()));

        when(salesQueryService.getOrderCodesByIds(Set.of(orderId)))
                .thenReturn(Map.of(orderId, "ORD-20261126-009"));

        Page<ReadableStockMovementResponse> page =
                service.getReadableMovements(variantId, "ALL", PageRequest.of(0, 20));

        assertEquals(2, page.getContent().size());

        ReadableStockMovementResponse r0 = page.getContent().get(0);
        assertEquals("Nhập mua hàng", r0.movementTypeLabel());
        assertEquals("GRN26112613068363", r0.documentCode());
        assertEquals(grId, r0.goodsReceiptId());
        assertEquals(poId, r0.purchaseOrderId());
        assertEquals("PO-2611-004", r0.poCode());
        assertEquals("Nhập kho từ TOTO Việt Nam (Đơn PO-2611-004)", r0.displayNote());

        ReadableStockMovementResponse r1 = page.getContent().get(1);
        assertEquals("Xuất kho", r1.movementTypeLabel());
        assertEquals("ORD-20261126-009", r1.documentCode());
        assertEquals(orderId, r1.orderId());
        assertEquals(deliveryId, r1.deliveryId());
        assertEquals("Xuất kho giao hàng cho đơn ORD-20261126-009", r1.displayNote());
        assertFalse(r1.displayNote().contains("operationId"));
    }

    @Test
    @DisplayName("AC-04: Truyền nhóm biến động không hợp lệ trả 400 INVALID_REQUEST_PARAM")
    void getReadableMovements_invalidGroupThrowsBadRequest() {
        UUID variantId = UUID.randomUUID();
        when(inventoryRepository.findByVariantId(variantId)).thenReturn(Optional.of(
                Inventory.builder().inventoryId(UUID.randomUUID()).variantId(variantId).build()));

        AppException ex = assertThrows(AppException.class,
                () -> service.getReadableMovements(variantId, "INVALID_GROUP", PageRequest.of(0, 10)));
        assertEquals(CommonErrorCode.INVALID_REQUEST_PARAM, ex.getErrorCode());
    }
}
