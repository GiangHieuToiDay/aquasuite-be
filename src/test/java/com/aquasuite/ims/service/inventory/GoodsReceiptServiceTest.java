package com.aquasuite.ims.service.inventory;

import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptItemRequest;
import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptRequest;
import com.aquasuite.ims.dto.inventory.response.GoodsReceiptResponse;
import com.aquasuite.ims.dto.purchasing.response.ReceivableItemView;
import com.aquasuite.ims.dto.purchasing.response.ReceivablePurchaseOrderView;
import com.aquasuite.ims.entity.inventory.GoodsReceipt;
import com.aquasuite.ims.entity.inventory.GoodsReceiptItem;
import com.aquasuite.ims.entity.inventory.Inventory;
import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.enums.inventory.MovementType;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.BaseErrorCode;
import com.aquasuite.ims.exception.inventory.InventoryErrorCode;
import com.aquasuite.ims.exception.purchasing.PurchasingErrorCode;
import com.aquasuite.ims.repository.inventory.GoodsReceiptItemRepository;
import com.aquasuite.ims.repository.inventory.GoodsReceiptRepository;
import com.aquasuite.ims.repository.inventory.InventoryRepository;
import com.aquasuite.ims.repository.inventory.StockMovementRepository;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.impl.GoodsReceiptServiceImpl;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.function.Executable;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test UC-32 (Hướng B) với Mockito. Purchasing được giả lập có trạng thái (số đã nhận theo dòng PO)
 * để kiểm tra nhận nhiều phiếu; tính nguyên tử/khóa thực tế được kiểm ở {@code GoodsReceiptPostgresTest}.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class GoodsReceiptServiceTest {

    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private PurchaseOrderService purchasingApi;
    @Mock private IamQueryService iamApi;

    private GoodsReceiptServiceImpl service;

    private final UUID poId = UUID.randomUUID();
    private final UUID poItemId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    /** Trạng thái giả lập của PO: đặt 10, đã nhận theo dòng. */
    private String poStatus;
    private final BigDecimal ordered = new BigDecimal("10");
    private BigDecimal received;
    /** Tồn giả lập của (variant, WH_MAIN). */
    private Inventory inventory;
    private final List<GoodsReceipt> savedReceipts = new ArrayList<>();
    private final Map<UUID, GoodsReceipt> receiptsByKey = new HashMap<>();

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptServiceImpl(
                goodsReceiptRepository, goodsReceiptItemRepository, inventoryRepository,
                stockMovementRepository, purchasingApi, iamApi);
        poStatus = "CONFIRMED";
        received = BigDecimal.ZERO;
        inventory = null;

        when(purchasingApi.lockReceivablePurchaseOrder(poId)).thenAnswer(inv -> Optional.of(view()));
        when(purchasingApi.applyGoodsReceipt(eq(poId), anyMap())).thenAnswer(inv -> {
            Map<UUID, BigDecimal> map = inv.getArgument(1);
            received = received.add(map.get(poItemId));
            poStatus = received.compareTo(ordered) >= 0 ? "COMPLETED" : "PARTIALLY_RECEIVED";
            return poStatus;
        });
        when(goodsReceiptRepository.findByIdempotencyKey(any()))
                .thenAnswer(inv -> Optional.ofNullable(receiptsByKey.get((UUID) inv.getArgument(0))));
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            if (g.getGoodsReceiptId() == null) {
                g.setGoodsReceiptId(UUID.randomUUID());
                savedReceipts.add(g);
            }
            receiptsByKey.put(g.getIdempotencyKey(), g);
            return g;
        });
        when(goodsReceiptItemRepository.save(any(GoodsReceiptItem.class))).thenAnswer(inv -> {
            GoodsReceiptItem i = inv.getArgument(0);
            if (i.getGoodsReceiptItemId() == null) i.setGoodsReceiptItemId(UUID.randomUUID());
            return i;
        });
        when(inventoryRepository.insertEmptyIfAbsent(any(), eq(variantId), eq("WH_MAIN"))).thenAnswer(inv -> {
            if (inventory != null) return 0;
            inventory = Inventory.builder().inventoryId(inv.getArgument(0)).variantId(variantId)
                    .warehouseCode("WH_MAIN").onHandQty(BigDecimal.ZERO).reservedQty(BigDecimal.ZERO)
                    .minimumQty(BigDecimal.ZERO).build();
            return 1;
        });
        when(inventoryRepository.findByVariantIdAndWarehouseCodeForUpdate(variantId, "WH_MAIN"))
                .thenAnswer(inv -> Optional.ofNullable(inventory));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private ReceivablePurchaseOrderView view() {
        ReceivableItemView item = new ReceivableItemView(
                poItemId, variantId, "SKU-A", "Bồn cầu", "cái", new BigDecimal("500000"),
                ordered, received, ordered.subtract(received).max(BigDecimal.ZERO));
        boolean receivable = "CONFIRMED".equals(poStatus) || "PARTIALLY_RECEIVED".equals(poStatus);
        return new ReceivablePurchaseOrderView(poId, "PO-001", poStatus, receivable, "WH_MAIN",
                UUID.randomUUID(), List.of(item));
    }

    private CreateGoodsReceiptRequest request(UUID key, CreateGoodsReceiptItemRequest... lines) {
        return CreateGoodsReceiptRequest.builder()
                .idempotencyKey(key)
                .purchaseOrderId(poId)
                .note("Đợt giao")
                .items(List.of(lines))
                .build();
    }

    private CreateGoodsReceiptRequest request(String qty) {
        return request(UUID.randomUUID(), line(poItemId, qty));
    }

    private static CreateGoodsReceiptItemRequest line(UUID itemId, String qty) {
        return CreateGoodsReceiptItemRequest.builder()
                .purchaseOrderItemId(itemId)
                .quantity(qty == null ? null : new BigDecimal(qty))
                .build();
    }

    private static void assertCode(BaseErrorCode expected, Executable call) {
        AppException ex = assertThrows(AppException.class, call);
        assertEquals(expected, ex.getErrorCode());
    }

    private void assertNoSideEffects() {
        verify(goodsReceiptRepository, never()).save(any());
        verify(goodsReceiptItemRepository, never()).save(any());
        verify(inventoryRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(purchasingApi, never()).applyGoodsReceipt(any(), anyMap());
    }

    // ---------------------------------------------------------------- happy paths

    @Test
    @DisplayName("Nhận 7/10: phiếu CONFIRMED, tồn +7, thẻ kho GOODS_RECEIPT, PO PARTIALLY_RECEIVED theo dòng PO")
    void partialReceipt() {
        GoodsReceiptResponse res = service.createAndConfirm(request("7"), actorId);

        assertEquals("CONFIRMED", res.status());
        assertEquals(0, res.items().get(0).quantity().compareTo(new BigDecimal("7")));
        assertEquals(0, inventory.getOnHandQty().compareTo(new BigDecimal("7")));
        assertEquals("PARTIALLY_RECEIVED", poStatus);

        ArgumentCaptor<GoodsReceiptItem> itemCaptor = ArgumentCaptor.forClass(GoodsReceiptItem.class);
        verify(goodsReceiptItemRepository).save(itemCaptor.capture());
        assertEquals(variantId, itemCaptor.getValue().getVariantId());
        assertEquals(poItemId, itemCaptor.getValue().getPurchaseOrderItemId());

        ArgumentCaptor<StockMovement> moveCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(moveCaptor.capture());
        assertEquals(MovementType.GOODS_RECEIPT, moveCaptor.getValue().getMovementType());
        assertEquals(0, moveCaptor.getValue().getQuantity().compareTo(new BigDecimal("7")));
        assertEquals(0, moveCaptor.getValue().getBalanceAfter().compareTo(new BigDecimal("7")));
        assertEquals(res.receiptCode(), moveCaptor.getValue().getReferenceId());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<UUID, BigDecimal>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(purchasingApi).applyGoodsReceipt(eq(poId), mapCaptor.capture());
        assertEquals(Map.of(poItemId, new BigDecimal("7")), mapCaptor.getValue());

        GoodsReceipt saved = savedReceipts.get(0);
        assertNotNull(saved.getIdempotencyKey());
        assertEquals(64, saved.getRequestHash().length());
    }

    @Test
    @DisplayName("Nhận đủ qua hai phiếu: 7 rồi 3 → PO COMPLETED, tồn 10, hai phiếu riêng")
    void fullReceiptAcrossTwoReceipts() {
        GoodsReceiptResponse first = service.createAndConfirm(request("7"), actorId);
        GoodsReceiptResponse second = service.createAndConfirm(request("3"), actorId);

        assertNotEquals(first.goodsReceiptId(), second.goodsReceiptId());
        assertEquals("COMPLETED", poStatus);
        assertEquals(0, inventory.getOnHandQty().compareTo(BigDecimal.TEN));
        verify(stockMovementRepository, times(2)).save(any());
        // Lần hai đọc remaining mới nhất (3) dưới khóa nên thêm 1 nữa bị từ chối.
        assertCode(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_RECEIVABLE, () -> service.createAndConfirm(request("1"), actorId));
    }

    @Test
    @DisplayName("Dòng tồn đã có: cộng vào số cũ")
    void existingInventoryIsIncremented() {
        inventory = Inventory.builder().inventoryId(UUID.randomUUID()).variantId(variantId).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("3")).reservedQty(BigDecimal.ZERO).minimumQty(BigDecimal.ZERO).build();

        service.createAndConfirm(request("5"), actorId);

        assertEquals(0, inventory.getOnHandQty().compareTo(new BigDecimal("8")));
    }

    @Test
    @DisplayName("Khóa PO trước khi đọc remaining; không dùng snapshot không khóa")
    void readsRemainingUnderPoLock() {
        service.createAndConfirm(request("2"), actorId);

        var order = inOrder(goodsReceiptRepository, purchasingApi, inventoryRepository);
        order.verify(goodsReceiptRepository).lockIdempotencyKey(anyString());
        order.verify(purchasingApi).lockReceivablePurchaseOrder(poId);
        order.verify(inventoryRepository).findByVariantIdAndWarehouseCodeForUpdate(variantId, "WH_MAIN");
        order.verify(purchasingApi).applyGoodsReceipt(eq(poId), anyMap());
        verify(purchasingApi, never()).getReceivablePurchaseOrder(any());
    }

    // ---------------------------------------------------------------- validation

    @Test
    @DisplayName("PO không tồn tại → INV_010")
    void poNotFound() {
        when(purchasingApi.lockReceivablePurchaseOrder(poId)).thenReturn(Optional.empty());
        assertCode(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_FOUND, () -> service.createAndConfirm(request("1"), actorId));
        assertNoSideEffects();
    }

    @Test
    @DisplayName("PO DRAFT/COMPLETED/CANCELLED → INV_011, không đổi gì")
    void poNotReceivable() {
        for (String status : List.of("DRAFT", "COMPLETED", "CANCELLED")) {
            poStatus = status;
            assertCode(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_RECEIVABLE,
                    () -> service.createAndConfirm(request("1"), actorId));
        }
        assertNoSideEffects();
    }

    @Test
    @DisplayName("Phiếu rỗng → INV_012")
    void emptyReceipt() {
        assertCode(InventoryErrorCode.GOODS_RECEIPT_EMPTY_ITEMS,
                () -> service.createAndConfirm(request(UUID.randomUUID()), actorId));
        assertNoSideEffects();
    }

    @Test
    @DisplayName("Dòng PO lặp → INV_013")
    void duplicateLine() {
        assertCode(InventoryErrorCode.GOODS_RECEIPT_DUPLICATE_ITEM, () -> service.createAndConfirm(
                request(UUID.randomUUID(), line(poItemId, "1"), line(poItemId, "2")), actorId));
        assertNoSideEffects();
    }

    @Test
    @DisplayName("Dòng ngoài PO → INV_014")
    void lineNotInPo() {
        assertCode(InventoryErrorCode.GOODS_RECEIPT_ITEM_NOT_IN_PO, () -> service.createAndConfirm(
                request(UUID.randomUUID(), line(UUID.randomUUID(), "1")), actorId));
        assertNoSideEffects();
    }

    @Test
    @DisplayName("Số lượng 0, âm hoặc thiếu → INV_015")
    void nonPositiveQuantity() {
        for (String qty : new String[]{"0", "-1", null}) {
            assertCode(InventoryErrorCode.GOODS_RECEIPT_INVALID_QUANTITY,
                    () -> service.createAndConfirm(request(UUID.randomUUID(), line(poItemId, qty)), actorId));
        }
        assertNoSideEffects();
    }

    @Test
    @DisplayName("Vượt remaining mới nhất → INV_016")
    void exceedsRemaining() {
        received = new BigDecimal("7");
        poStatus = "PARTIALLY_RECEIVED";
        assertCode(InventoryErrorCode.GOODS_RECEIPT_EXCEEDS_REMAINING, () -> service.createAndConfirm(request("4"), actorId));
        assertNoSideEffects();
    }

    // ---------------------------------------------------------------- idempotency

    @Test
    @DisplayName("Retry cùng key + cùng payload: trả phiếu cũ, không cộng tồn lần hai")
    void retrySameKeySamePayloadReplays() {
        UUID key = UUID.randomUUID();
        GoodsReceiptResponse first = service.createAndConfirm(request(key, line(poItemId, "7")), actorId);
        when(goodsReceiptItemRepository.findByGoodsReceiptId(first.goodsReceiptId())).thenReturn(List.of(
                GoodsReceiptItem.builder().goodsReceiptItemId(UUID.randomUUID()).goodsReceiptId(first.goodsReceiptId())
                        .purchaseOrderItemId(poItemId).variantId(variantId).quantity(new BigDecimal("7.00")).build()));

        // Payload tương đương (7.00 == 7) vẫn được coi là cùng yêu cầu.
        GoodsReceiptResponse retry = service.createAndConfirm(request(key, line(poItemId, "7.00")), actorId);

        assertEquals(first.goodsReceiptId(), retry.goodsReceiptId());
        assertEquals(first.receiptCode(), retry.receiptCode());
        assertEquals(1, retry.items().size());
        assertEquals(0, inventory.getOnHandQty().compareTo(new BigDecimal("7")));
        assertEquals(0, received.compareTo(new BigDecimal("7")));
        verify(stockMovementRepository, times(1)).save(any());
        verify(purchasingApi, times(1)).applyGoodsReceipt(any(), anyMap());
        verify(purchasingApi, times(1)).lockReceivablePurchaseOrder(any());
    }

    @Test
    @DisplayName("Cùng key nhưng payload khác → INV_019 (409), không ghi thêm")
    void sameKeyDifferentPayloadConflicts() {
        UUID key = UUID.randomUUID();
        service.createAndConfirm(request(key, line(poItemId, "2")), actorId);

        assertCode(InventoryErrorCode.GOODS_RECEIPT_IDEMPOTENCY_CONFLICT,
                () -> service.createAndConfirm(request(key, line(poItemId, "3")), actorId));

        assertEquals(0, inventory.getOnHandQty().compareTo(new BigDecimal("2")));
        verify(stockMovementRepository, times(1)).save(any());
        verify(purchasingApi, times(1)).applyGoodsReceipt(any(), anyMap());
    }

    @Test
    @DisplayName("Đổi ghi chú/hóa đơn với cùng key cũng là payload khác → INV_019")
    void sameKeyDifferentHeaderFieldConflicts() {
        UUID key = UUID.randomUUID();
        service.createAndConfirm(request(key, line(poItemId, "2")), actorId);
        CreateGoodsReceiptRequest changedNote = CreateGoodsReceiptRequest.builder()
                .idempotencyKey(key).purchaseOrderId(poId).note("Ghi chú khác")
                .items(List.of(line(poItemId, "2"))).build();

        assertCode(InventoryErrorCode.GOODS_RECEIPT_IDEMPOTENCY_CONFLICT,
                () -> service.createAndConfirm(changedNote, actorId));
    }

    // ---------------------------------------------------------------- failure propagation

    @Test
    @DisplayName("PO bị hủy/đóng giữa chừng → lỗi từ Purchasing được ném ra (transaction cuộn ngược)")
    void purchasingRejectionPropagates() {
        when(purchasingApi.applyGoodsReceipt(eq(poId), anyMap()))
                .thenThrow(new AppException(PurchasingErrorCode.PO_NOT_RECEIVABLE, "CANCELLED"));

        assertCode(PurchasingErrorCode.PO_NOT_RECEIVABLE, () -> service.createAndConfirm(request("2"), actorId));
    }

    @Test
    @DisplayName("Ghi thẻ kho lỗi → ném lỗi, không cập nhật tiến độ PO")
    void movementFailurePropagates() {
        when(stockMovementRepository.save(any())).thenThrow(new IllegalStateException("db down"));

        assertThrows(IllegalStateException.class, () -> service.createAndConfirm(request("2"), actorId));
        verify(purchasingApi, never()).applyGoodsReceipt(any(), anyMap());
    }
}
