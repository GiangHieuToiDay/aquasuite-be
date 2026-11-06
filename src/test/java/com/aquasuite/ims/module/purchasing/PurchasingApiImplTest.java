package com.aquasuite.ims.module.purchasing;

import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrder;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderItem;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderItemRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.module.purchasing.internal.service.impl.PurchasingApiImpl;
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
class PurchasingApiImplTest {

    @Mock
    private PurchaseOrderRepository purchaseOrderRepository;

    @Mock
    private PurchaseOrderItemRepository purchaseOrderItemRepository;

    private PurchasingApiImpl purchasingApi;

    @BeforeEach
    void setUp() {
        purchasingApi = new PurchasingApiImpl(purchaseOrderRepository, purchaseOrderItemRepository);
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

    @Test
    @DisplayName("onGoodsReceiptConfirmed - Update received quantity and set PARTIALLY_RECEIVED")
    void testOnGoodsReceiptConfirmed_PartiallyReceived() {
        UUID poId = UUID.randomUUID();
        UUID vId1 = UUID.randomUUID();
        UUID vId2 = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .poCode("PO-001")
                .status(PurchaseOrderStatus.CONFIRMED)
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .variantId(vId1)
                .quantityOrdered(new BigDecimal("10"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        PurchaseOrderItem item2 = PurchaseOrderItem.builder()
                .variantId(vId2)
                .quantityOrdered(new BigDecimal("20"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        po.addItem(item1);
        po.addItem(item2);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        purchasingApi.onGoodsReceiptConfirmed(poId, Map.of(vId1, new BigDecimal("5")));

        assertEquals(PurchaseOrderStatus.PARTIALLY_RECEIVED, po.getStatus());
        assertEquals(new BigDecimal("5"), item1.getQuantityReceived());
        assertEquals(BigDecimal.ZERO, item2.getQuantityReceived());
        verify(purchaseOrderRepository).save(po);
    }

    @Test
    @DisplayName("onGoodsReceiptConfirmed - Update received quantity and set COMPLETED when all items full")
    void testOnGoodsReceiptConfirmed_Completed() {
        UUID poId = UUID.randomUUID();
        UUID vId1 = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId)
                .poCode("PO-001")
                .status(PurchaseOrderStatus.CONFIRMED)
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .variantId(vId1)
                .quantityOrdered(new BigDecimal("10"))
                .quantityReceived(BigDecimal.ZERO)
                .build();
        po.addItem(item1);

        when(purchaseOrderRepository.findById(poId)).thenReturn(Optional.of(po));

        purchasingApi.onGoodsReceiptConfirmed(poId, Map.of(vId1, new BigDecimal("10")));

        assertEquals(PurchaseOrderStatus.COMPLETED, po.getStatus());
        assertEquals(new BigDecimal("10"), item1.getQuantityReceived());
        verify(purchaseOrderRepository).save(po);
    }
}