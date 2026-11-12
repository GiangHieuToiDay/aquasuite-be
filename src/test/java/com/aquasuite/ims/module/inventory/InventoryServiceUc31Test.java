package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.event.InventoryThresholdChangedEvent;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.enums.InventoryStockStatus;
import com.aquasuite.ims.module.inventory.internal.error.InventoryErrorCode;
import com.aquasuite.ims.module.inventory.internal.payload.res.InventoryListItemResponse;
import com.aquasuite.ims.module.inventory.internal.repository.GoodsReceiptRepository;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockCountItemRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockMovementRepository;
import com.aquasuite.ims.module.inventory.internal.service.impl.InventoryServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test cho UC-31 (Tra cứu &amp; Quản lý Tồn kho): kiểm tra validate ngưỡng,
 * phát sự kiện audit, biên tồn thấp và enrich danh sách — dùng Mockito, không cần Spring/DB.
 */
@ExtendWith(MockitoExtension.class)
class InventoryServiceUc31Test {

    @Mock private InventoryRepository inventoryRepository;
    @Mock private StockMovementRepository stockMovementRepository;
    @Mock private StockCountItemRepository stockCountItemRepository;
    @Mock private GoodsReceiptRepository goodsReceiptRepository;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private IamApi iamApi;
    @Mock private CatalogApi catalogApi;

    @InjectMocks private InventoryServiceImpl service;

    private Inventory inventory(BigDecimal onHand, BigDecimal reserved, BigDecimal min, BigDecimal max) {
        return Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .variantId(UUID.randomUUID())
                .warehouseCode("WH_MAIN")
                .onHandQty(onHand)
                .reservedQty(reserved)
                .minimumQty(min)
                .maximumQty(max)
                .build();
    }

    @Test
    @DisplayName("AC-04: Cấu hình ngưỡng hợp lệ -> lưu và phát sự kiện audit với giá trị trước/sau")
    void updateThreshold_valid_publishesAuditEvent() {
        Inventory inv = inventory(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("5"), null);
        UUID variantId = inv.getVariantId();
        UUID actor = UUID.randomUUID();
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.of(inv));

        service.updateThreshold(variantId, new BigDecimal("10"), new BigDecimal("100"), actor);

        assertEquals(new BigDecimal("10"), inv.getMinimumQty());
        assertEquals(new BigDecimal("100"), inv.getMaximumQty());
        verify(inventoryRepository).save(inv);

        ArgumentCaptor<InventoryThresholdChangedEvent> captor =
                ArgumentCaptor.forClass(InventoryThresholdChangedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        InventoryThresholdChangedEvent event = captor.getValue();
        assertEquals(actor, event.actorId());
        assertEquals(new BigDecimal("5"), event.oldMinimumQty());
        assertEquals(new BigDecimal("10"), event.newMinimumQty());
        assertEquals(new BigDecimal("100"), event.newMaximumQty());
    }

    @Test
    @DisplayName("AC-05: minimumQty âm bị từ chối INVALID_THRESHOLD")
    void updateThreshold_negativeMin_rejected() {
        AppException ex = assertThrows(AppException.class,
                () -> service.updateThreshold(UUID.randomUUID(), new BigDecimal("-1"), null, UUID.randomUUID()));
        assertEquals(InventoryErrorCode.INVALID_THRESHOLD, ex.getErrorCode());
        verifyNoInteractions(eventPublisher);
    }

    @Test
    @DisplayName("AC-05: maximumQty < minimumQty bị từ chối INVALID_THRESHOLD")
    void updateThreshold_maxLessThanMin_rejected() {
        AppException ex = assertThrows(AppException.class,
                () -> service.updateThreshold(UUID.randomUUID(), new BigDecimal("10"), new BigDecimal("5"), UUID.randomUUID()));
        assertEquals(InventoryErrorCode.INVALID_THRESHOLD, ex.getErrorCode());
        verify(inventoryRepository, never()).findByVariantIdForUpdate(any());
    }

    @Test
    @DisplayName("Cấu hình ngưỡng cho SKU không tồn tại -> INVENTORY_NOT_FOUND")
    void updateThreshold_notFound() {
        UUID variantId = UUID.randomUUID();
        when(inventoryRepository.findByVariantIdForUpdate(variantId)).thenReturn(Optional.empty());
        AppException ex = assertThrows(AppException.class,
                () -> service.updateThreshold(variantId, new BigDecimal("10"), null, UUID.randomUUID()));
        assertEquals(InventoryErrorCode.INVENTORY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("AC-03/BR-02: tồn thấp = minimumQty>0 và available<=minimumQty")
    void isLowStock_boundary() {
        assertTrue(inventory(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"), null).isLowStock());
        assertFalse(inventory(new BigDecimal("6"), BigDecimal.ZERO, new BigDecimal("5"), null).isLowStock());
        assertFalse(inventory(new BigDecimal("1"), BigDecimal.ZERO, BigDecimal.ZERO, null).isLowStock(),
                "minimumQty=0 nghĩa là không theo dõi ngưỡng -> không cảnh báo");
    }

    @Test
    @DisplayName("AC-01: searchInventory enrich SKU từ Catalog và suy ra trạng thái đúng")
    void searchInventory_enrichesAndComputesStatus() {
        Inventory low = inventory(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"), null);
        Pageable pageable = PageRequest.of(0, 20);
        Page<Inventory> page = new PageImpl<>(List.of(low), pageable, 1);
        when(inventoryRepository.search(eq(false), eq(null), eq(pageable))).thenReturn(page);
        when(catalogApi.getSkuSummaries(List.of(low.getVariantId()))).thenReturn(Map.of(
                low.getVariantId(),
                CatalogSkuSummaryDto.builder()
                        .variantId(low.getVariantId()).sku("SKU-1").productName("Bồn cầu TOTO").unit("cái").build()));

        Page<InventoryListItemResponse> result = service.searchInventory(null, null, false, pageable);

        assertEquals(1, result.getTotalElements());
        InventoryListItemResponse item = result.getContent().getFirst();
        assertEquals("SKU-1", item.sku());
        assertEquals("Bồn cầu TOTO", item.productName());
        assertEquals(new BigDecimal("5"), item.availableQty());
        assertEquals(InventoryStockStatus.LOW_STOCK, item.status());
        assertTrue(item.lowStock());
    }

    @Test
    @DisplayName("AC-02: từ khóa không khớp SKU nào -> trả về trang rỗng, không gọi repository")
    void searchInventory_keywordNoMatch_returnsEmpty() {
        when(catalogApi.getAllActiveSkus()).thenReturn(List.of());
        Page<InventoryListItemResponse> result = service.searchInventory("khong-ton-tai", null, false, PageRequest.of(0, 20));
        assertTrue(result.isEmpty());
        verify(inventoryRepository, never()).searchByVariantIds(any(), anyBoolean(), any(), any());
    }

    @Test
    @DisplayName("AC-03: getLowStockItems chỉ truy vấn nhánh lowStockOnly=true")
    void getLowStockItems_usesLowStockFilter() {
        Pageable pageable = PageRequest.of(0, 20);
        when(inventoryRepository.search(eq(true), eq(null), eq(pageable))).thenReturn(Page.empty(pageable));
        service.getLowStockItems(pageable);
        verify(inventoryRepository).search(true, null, pageable);
    }
}
