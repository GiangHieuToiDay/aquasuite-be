package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.dto.MovementType;
import com.aquasuite.ims.module.inventory.internal.entity.GoodsReceipt;
import com.aquasuite.ims.module.inventory.internal.entity.GoodsReceiptItem;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.payload.req.CreateGoodsReceiptItemRequest;
import com.aquasuite.ims.module.inventory.internal.payload.req.CreateGoodsReceiptRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.GoodsReceiptResponse;
import com.aquasuite.ims.module.inventory.internal.repository.GoodsReceiptItemRepository;
import com.aquasuite.ims.module.inventory.internal.repository.GoodsReceiptRepository;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockMovementRepository;
import com.aquasuite.ims.module.inventory.internal.service.impl.GoodsReceiptServiceImpl;
import com.aquasuite.ims.module.purchasing.api.PurchasingApi;
import com.aquasuite.ims.module.purchasing.api.dto.ReceivableItemView;
import com.aquasuite.ims.module.purchasing.api.dto.ReceivablePurchaseOrderView;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho nghiệp vụ nhập kho UC-32 (Hướng B): kiểm tra các Acceptance Criteria chính
 * bằng Mockito, không cần context Spring hay database thật.
 */
@ExtendWith(MockitoExtension.class)
class GoodsReceiptServiceTest {

    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private GoodsReceiptItemRepository goodsReceiptItemRepository;
    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private PurchasingApi purchasingApi;
    @Mock private IamApi iamApi;

    private GoodsReceiptServiceImpl service;

    private final UUID poId = UUID.randomUUID();
    private final UUID poItemId = UUID.randomUUID();
    private final UUID variantId = UUID.randomUUID();
    private final UUID actorId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new GoodsReceiptServiceImpl(
                goodsReceiptRepository, goodsReceiptItemRepository, inventoryRepository,
                stockMovementRepository, purchasingApi, iamApi);
    }

    /** Dựng snapshot PO đang nhận được với một dòng: đặt {ordered}, đã nhận {received}. */
    private void mockReceivablePo(String ordered, String received) {
        ReceivableItemView item = new ReceivableItemView(
                poItemId, variantId, "SKU-A", "Bồn cầu", "cái",
                new BigDecimal("500000"),
                new BigDecimal(ordered), new BigDecimal(received),
                new BigDecimal(ordered).subtract(new BigDecimal(received)).max(BigDecimal.ZERO));
        ReceivablePurchaseOrderView view = new ReceivablePurchaseOrderView(
                poId, "PO-001", "CONFIRMED", true, "WH_MAIN", UUID.randomUUID(), List.of(item));
        when(purchasingApi.getReceivablePurchaseOrder(poId)).thenReturn(Optional.of(view));
    }

    private CreateGoodsReceiptRequest request(String qty) {
        return CreateGoodsReceiptRequest.builder()
                .purchaseOrderId(poId)
                .items(List.of(CreateGoodsReceiptItemRequest.builder()
                        .purchaseOrderItemId(poItemId)
                        .quantity(new BigDecimal(qty))
                        .build()))
                .build();
    }

    @Test
    @DisplayName("AC-02: Nhận 7/10 - cộng tồn +7, ghi thẻ kho GOODS_RECEIPT, báo tiến độ cho PO")
    void testReceivePartial() {
        mockReceivablePo("10", "0");
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            if (g.getGoodsReceiptId() == null) g.setGoodsReceiptId(UUID.randomUUID());
            return g;
        });
        when(goodsReceiptItemRepository.save(any(GoodsReceiptItem.class))).thenAnswer(inv -> {
            GoodsReceiptItem i = inv.getArgument(0);
            if (i.getGoodsReceiptItemId() == null) i.setGoodsReceiptItemId(UUID.randomUUID());
            return i;
        });
        when(inventoryRepository.findByVariantIdAndWarehouseCodeForUpdate(variantId, "WH_MAIN"))
                .thenReturn(Optional.empty());
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        GoodsReceiptResponse res = service.createAndConfirm(request("7"), actorId);

        assertEquals("CONFIRMED", res.status());
        assertEquals(0, res.items().get(0).quantity().compareTo(new BigDecimal("7")));

        // Dòng phiếu phải có variant_id (cột NOT NULL ở DB) để không vi phạm ràng buộc khi insert.
        ArgumentCaptor<GoodsReceiptItem> itemCaptor = ArgumentCaptor.forClass(GoodsReceiptItem.class);
        verify(goodsReceiptItemRepository).save(itemCaptor.capture());
        assertEquals(variantId, itemCaptor.getValue().getVariantId());

        // Tồn được cộng đúng +7 và thẻ kho đúng loại GOODS_RECEIPT.
        ArgumentCaptor<StockMovement> moveCaptor = ArgumentCaptor.forClass(StockMovement.class);
        verify(stockMovementRepository).save(moveCaptor.capture());
        assertEquals(MovementType.GOODS_RECEIPT, moveCaptor.getValue().getMovementType());
        assertEquals(0, moveCaptor.getValue().getQuantity().compareTo(new BigDecimal("7")));
        assertEquals(0, moveCaptor.getValue().getBalanceAfter().compareTo(new BigDecimal("7")));

        // Báo tiến độ cho purchasing đúng số nhận theo variant.
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<UUID, BigDecimal>> mapCaptor = ArgumentCaptor.forClass(Map.class);
        verify(purchasingApi).onGoodsReceiptConfirmed(eq(poId), mapCaptor.capture());
        assertEquals(0, mapCaptor.getValue().get(variantId).compareTo(new BigDecimal("7")));
    }

    @Test
    @DisplayName("AC-02: Cộng tồn vào dòng tồn đã có thay vì tạo mới")
    void testReceiveExistingInventory() {
        mockReceivablePo("10", "0");
        when(goodsReceiptRepository.save(any(GoodsReceipt.class))).thenAnswer(inv -> {
            GoodsReceipt g = inv.getArgument(0);
            if (g.getGoodsReceiptId() == null) g.setGoodsReceiptId(UUID.randomUUID());
            return g;
        });
        when(goodsReceiptItemRepository.save(any(GoodsReceiptItem.class))).thenAnswer(inv -> inv.getArgument(0));
        Inventory existing = Inventory.builder()
                .inventoryId(UUID.randomUUID()).variantId(variantId).warehouseCode("WH_MAIN")
                .onHandQty(new BigDecimal("3")).reservedQty(BigDecimal.ZERO).minimumQty(BigDecimal.ZERO)
                .build();
        when(inventoryRepository.findByVariantIdAndWarehouseCodeForUpdate(variantId, "WH_MAIN"))
                .thenReturn(Optional.of(existing));
        when(inventoryRepository.save(any(Inventory.class))).thenAnswer(inv -> inv.getArgument(0));

        service.createAndConfirm(request("5"), actorId);

        // 3 + 5 = 8
        assertEquals(0, existing.getOnHandQty().compareTo(new BigDecimal("8")));
    }

    @Test
    @DisplayName("AC-05: Nhận vượt số còn lại bị từ chối, không đụng tồn hay PO")
    void testReceiveExceedsRemaining() {
        mockReceivablePo("10", "7"); // còn lại 3

        assertThrows(AppException.class, () -> service.createAndConfirm(request("5"), actorId));

        verify(goodsReceiptRepository, never()).save(any());
        verify(stockMovementRepository, never()).save(any());
        verify(purchasingApi, never()).onGoodsReceiptConfirmed(any(), any());
    }

    @Test
    @DisplayName("AC-05: Dòng nhập không thuộc PO bị từ chối")
    void testReceiveItemNotInPo() {
        mockReceivablePo("10", "0");
        CreateGoodsReceiptRequest req = CreateGoodsReceiptRequest.builder()
                .purchaseOrderId(poId)
                .items(List.of(CreateGoodsReceiptItemRequest.builder()
                        .purchaseOrderItemId(UUID.randomUUID()) // không thuộc PO
                        .quantity(new BigDecimal("1"))
                        .build()))
                .build();

        assertThrows(AppException.class, () -> service.createAndConfirm(req, actorId));
        verify(stockMovementRepository, never()).save(any());
    }

    @Test
    @DisplayName("AC-03/điều kiện: PO không ở trạng thái cho phép nhập thì bị từ chối")
    void testReceiveNotReceivablePo() {
        ReceivablePurchaseOrderView view = new ReceivablePurchaseOrderView(
                poId, "PO-001", "DRAFT", false, "WH_MAIN", UUID.randomUUID(), List.of());
        when(purchasingApi.getReceivablePurchaseOrder(poId)).thenReturn(Optional.of(view));

        assertThrows(AppException.class, () -> service.createAndConfirm(request("1"), actorId));
        verify(goodsReceiptRepository, never()).save(any());
    }

    @Test
    @DisplayName("PO không tồn tại thì báo lỗi không tìm thấy")
    void testReceivePoNotFound() {
        when(purchasingApi.getReceivablePurchaseOrder(poId)).thenReturn(Optional.empty());
        assertThrows(AppException.class, () -> service.createAndConfirm(request("1"), actorId));
    }
}
