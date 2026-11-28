package com.aquasuite.ims.service.purchasing;

import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderBriefView;
import com.aquasuite.ims.dto.purchasing.response.VariantPurchaseOrderLineView;
import com.aquasuite.ims.entity.inventory.Supplier;
import com.aquasuite.ims.entity.purchasing.PurchaseOrder;
import com.aquasuite.ims.entity.purchasing.PurchaseOrderItem;
import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import com.aquasuite.ims.repository.inventory.SupplierRepository;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderItemRepository;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderRepository;
import com.aquasuite.ims.service.purchasing.impl.PurchaseOrderTrackingQueryServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PurchaseOrderTrackingQueryServiceTest {

    @Mock private PurchaseOrderItemRepository purchaseOrderItemRepository;
    @Mock private PurchaseOrderRepository purchaseOrderRepository;
    @Mock private SupplierRepository supplierRepository;

    @InjectMocks private PurchaseOrderTrackingQueryServiceImpl queryService;

    @Test
    @DisplayName("Trả các dòng PO mở sắp xếp ưu tiên ngày hẹn giao sớm nhất và gắn tên NCC")
    void findLinesByVariantIds_mapsAndSortsCorrectly() {
        UUID variantId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();
        UUID po1Id = UUID.randomUUID();
        UUID po2Id = UUID.randomUUID();

        PurchaseOrder poLater = PurchaseOrder.builder()
                .purchaseOrderId(po1Id).poCode("PO-LATER").supplierId(supplierId)
                .status(PurchaseOrderStatus.CONFIRMED)
                .orderDate(LocalDate.now()).expectedDate(LocalDate.now().plusDays(5))
                .build();
        PurchaseOrder poSooner = PurchaseOrder.builder()
                .purchaseOrderId(po2Id).poCode("PO-SOONER").supplierId(supplierId)
                .status(PurchaseOrderStatus.PARTIALLY_RECEIVED)
                .orderDate(LocalDate.now()).expectedDate(LocalDate.now().plusDays(1))
                .build();

        PurchaseOrderItem item1 = PurchaseOrderItem.builder()
                .purchaseOrderItemId(UUID.randomUUID()).purchaseOrder(poLater).variantId(variantId)
                .unit("Bộ").quantityOrdered(new BigDecimal("10")).quantityReceived(BigDecimal.ZERO)
                .build();
        PurchaseOrderItem item2 = PurchaseOrderItem.builder()
                .purchaseOrderItemId(UUID.randomUUID()).purchaseOrder(poSooner).variantId(variantId)
                .unit("Bộ").quantityOrdered(new BigDecimal("26")).quantityReceived(new BigDecimal("6"))
                .build();

        when(purchaseOrderItemRepository.findItemsWithOrderByVariantIdsAndStatuses(eq(List.of(variantId)), any()))
                .thenReturn(List.of(item1, item2));
        when(supplierRepository.findAllById(Set.of(supplierId))).thenReturn(List.of(
                Supplier.builder().supplierId(supplierId).supplierCode("SUP-TOTO").supplierName("TOTO Việt Nam").build()));

        List<VariantPurchaseOrderLineView> lines = queryService.findLinesByVariantIds(List.of(variantId), false);

        assertEquals(2, lines.size());
        assertEquals("PO-SOONER", lines.get(0).poCode());
        assertEquals("Nhận một phần", lines.get(0).statusLabel());
        assertEquals("TOTO Việt Nam", lines.get(0).supplierName());
        assertEquals(new BigDecimal("20"), lines.get(0).remainingQty());
        assertEquals("PO-LATER", lines.get(1).poCode());
    }

    @Test
    @DisplayName("Tra cứu tóm tắt PO theo danh sách ID trả đúng mã đơn, trạng thái tiếng Việt và tên NCC")
    void findOrderBriefsByIds_returnsMappedBriefs() {
        UUID poId = UUID.randomUUID();
        UUID supplierId = UUID.randomUUID();

        PurchaseOrder po = PurchaseOrder.builder()
                .purchaseOrderId(poId).poCode("PO-2611-004").supplierId(supplierId)
                .status(PurchaseOrderStatus.COMPLETED)
                .orderDate(LocalDate.now())
                .build();

        when(purchaseOrderRepository.findAllById(Set.of(poId))).thenReturn(List.of(po));
        when(supplierRepository.findAllById(Set.of(supplierId))).thenReturn(List.of(
                Supplier.builder().supplierId(supplierId).supplierCode("SUP-01").supplierName("TOTO Việt Nam").build()));

        Map<UUID, PurchaseOrderBriefView> map = queryService.findOrderBriefsByIds(Set.of(poId));

        assertEquals(1, map.size());
        assertEquals("PO-2611-004", map.get(poId).poCode());
        assertEquals("Hoàn thành", map.get(poId).statusLabel());
        assertEquals("TOTO Việt Nam", map.get(poId).supplierName());
    }
}
