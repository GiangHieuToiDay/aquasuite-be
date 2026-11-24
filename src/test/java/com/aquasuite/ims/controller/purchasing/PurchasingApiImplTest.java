package com.aquasuite.ims.controller.purchasing;

import com.aquasuite.ims.entity.purchasing.PurchaseOrder;
import com.aquasuite.ims.entity.purchasing.PurchaseOrderItem;
import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderItemRepository;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderRepository;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
import com.aquasuite.ims.service.purchasing.impl.PurchaseOrderServiceImpl;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.purchasing.PurchasingErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderReceiptConfirmationTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    @org.mockito.InjectMocks
    private PurchaseOrderServiceImpl purchasingApi;

    @BeforeEach
    void setUp() {
        // @InjectMocks handles injection
    }

    @Test
    @DisplayName("getIncomingStockQty - Sum remaining quantity from open PO items")
    void testGetIncomingStockQty() {
        UUID variantId = UUID.randomUUID();
        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .quantityOrdered(new BigDecimal("10"))
                .quantityReceived(new BigDecimal("2"))
                .build();
        PurchaseOrderItem item2 = PurchaseOrderItem.builder()
                .quantityOrdered(new BigDecimal("5"))
                .quantityReceived(BigDecimal.ZERO)
                .build();

        when(purchaseOrderItemRepository.findOpenItemsByVariantId(eq(variantId), any()))
                .thenReturn(List.of(item1, item2));

        BigDecimal incoming = purchasingApi.getIncomingStockQty(variantId);
        // (10 - 2) + (5 - 0) = 13
        assertEquals(new BigDecimal("13"), incoming);
    }

    private PurchaseOrder po(UUID poId, PurchaseOrderStatus status, PurchaseOrderItem... items) {
        PurchaseOrder po = PurchaseOrder.builder().purchaseOrderId(poId).poCode("PO-001").status(status).build();
        for (PurchaseOrderItem item : items) po.addItem(item);
        return po;
    }

    private PurchaseOrderItem item(UUID itemId, String ordered, String received) {
        return PurchaseOrderItem.builder()
                .purchaseOrderItemId(itemId)
                .variantId(UUID.randomUUID())
                .sku("SKU-" + itemId.toString().substring(0, 4))
                .quantityOrdered(new BigDecimal(ordered))
                .quantityReceived(new BigDecimal(received))
                .build();
    }

    @Test
    @DisplayName("applyGoodsReceipt - cộng đúng dòng PO theo purchaseOrderItemId, PO -> PARTIALLY_RECEIVED")
    void applyGoodsReceipt_partiallyReceived() {
        UUID poId = UUID.randomUUID();
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        PurchaseOrderItem item1 = item(itemId1, "10", "0");
        PurchaseOrderItem item2 = item(itemId2, "20", "0");
        PurchaseOrder po = po(poId, PurchaseOrderStatus.CONFIRMED, item1, item2);
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

        String status = purchasingApi.applyGoodsReceipt(poId, Map.of(itemId1, new BigDecimal("5")));

        assertEquals("PARTIALLY_RECEIVED", status);
        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());
        assertEquals(0, item1.getQuantityReceived().compareTo(new BigDecimal("5")));
        assertEquals(0, item2.getQuantityReceived().compareTo(BigDecimal.ZERO));
        verify(purchaseOrderRepository).save(po);
        verify(purchaseOrderRepository, never()).findById(any());
    }

    @Test
    @DisplayName("applyGoodsReceipt - phiếu thứ hai nhận nốt mọi dòng thì PO -> COMPLETED")
    void applyGoodsReceipt_completedAcrossReceipts() {
        UUID poId = UUID.randomUUID();
        UUID itemId1 = UUID.randomUUID();
        UUID itemId2 = UUID.randomUUID();
        PurchaseOrderItem item1 = item(itemId1, "10", "7");
        PurchaseOrderItem item2 = item(itemId2, "4", "4");
        PurchaseOrder po = po(poId, PurchaseOrderStatus.PARTIALLY_RECEIVED, item1, item2);
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

        String status = purchasingApi.applyGoodsReceipt(poId, Map.of(itemId1, new BigDecimal("3")));

        assertEquals("COMPLETED", status);
        assertEquals(0, item1.getQuantityReceived().compareTo(BigDecimal.TEN));
    }

    @Test
    @DisplayName("applyGoodsReceipt - vượt số còn lại thì ném lỗi, không lưu PO")
    void applyGoodsReceipt_exceedsRemaining() {
        UUID poId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseOrder po = po(poId, PurchaseOrderStatus.CONFIRMED, item(itemId, "10", "4"));
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

        AppException ex = assertThrows(AppException.class,
                () -> purchasingApi.applyGoodsReceipt(poId, Map.of(itemId, new BigDecimal("7"))));
        assertEquals(PurchasingErrorCode.PO_RECEIPT_EXCEEDS_REMAINING, ex.getErrorCode());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyGoodsReceipt - PO đã hủy/đóng: ném PO_021 thay vì bỏ qua im lặng")
    void applyGoodsReceipt_rejectsNonReceivablePo() {
        for (PurchaseOrderStatus status : List.of(PurchaseOrderStatus.CANCELLED, PurchaseOrderStatus.COMPLETED,
                PurchaseOrderStatus.DRAFT)) {
            UUID poId = UUID.randomUUID();
            UUID itemId = UUID.randomUUID();
            PurchaseOrderItem item = item(itemId, "10", "0");
            PurchaseOrder po = po(poId, status, item);
            when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

            AppException ex = assertThrows(AppException.class,
                    () -> purchasingApi.applyGoodsReceipt(poId, Map.of(itemId, BigDecimal.ONE)));
            assertEquals(PurchasingErrorCode.PO_NOT_RECEIVABLE, ex.getErrorCode());
            assertEquals(0, item.getQuantityReceived().compareTo(BigDecimal.ZERO));
        }
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("applyGoodsReceipt - dòng không thuộc PO hoặc số <= 0 bị từ chối")
    void applyGoodsReceipt_rejectsForeignOrNonPositiveLine() {
        UUID poId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseOrder po = po(poId, PurchaseOrderStatus.CONFIRMED, item(itemId, "10", "0"));
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

        AppException foreign = assertThrows(AppException.class,
                () -> purchasingApi.applyGoodsReceipt(poId, Map.of(UUID.randomUUID(), BigDecimal.ONE)));
        assertEquals(PurchasingErrorCode.PO_RECEIPT_ITEM_NOT_IN_PO, foreign.getErrorCode());
        AppException zero = assertThrows(AppException.class,
                () -> purchasingApi.applyGoodsReceipt(poId, Map.of(itemId, BigDecimal.ZERO)));
        assertEquals(PurchasingErrorCode.PO_RECEIPT_ITEM_NOT_IN_PO, zero.getErrorCode());
        verify(purchaseOrderRepository, never()).save(any());
    }

    @Test
    @DisplayName("lockReceivablePurchaseOrder - đọc qua FOR UPDATE và trả remaining mới nhất")
    void lockReceivablePurchaseOrder_usesLockingRead() {
        UUID poId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        PurchaseOrder po = po(poId, PurchaseOrderStatus.PARTIALLY_RECEIVED, item(itemId, "10", "7"));
        when(purchaseOrderRepository.findByIdForUpdate(poId)).thenReturn(Optional.of(po));

        var view = purchasingApi.lockReceivablePurchaseOrder(poId).orElseThrow();

        assertTrue(view.receivable());
        assertEquals(0, view.items().get(0).remainingQty().compareTo(new BigDecimal("3")));
        verify(purchaseOrderRepository, never()).findById(any());
    }
}
