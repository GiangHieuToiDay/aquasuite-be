package com.aquasuite.ims.service.inventory;

import com.aquasuite.ims.dto.inventory.command.StockAdjustCommand;
import com.aquasuite.ims.dto.inventory.command.StockDispatchCommand;
import com.aquasuite.ims.dto.inventory.response.GoodsReceiptSummaryDto;
import com.aquasuite.ims.dto.inventory.response.InventoryListItemResponse;
import com.aquasuite.ims.dto.inventory.response.StockDispatchResult;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;
import com.aquasuite.ims.dto.inventory.response.VariantInventoryUsage;
import com.aquasuite.ims.entity.inventory.StockMovement;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface InventoryService {

    StockSummaryDto getInventoryByVariantId(UUID variantId);

    Page<StockMovement> getMovementsByVariantId(UUID variantId, Pageable pageable);

    Page<InventoryListItemResponse> searchInventory(String keyword, String status, boolean lowStockOnly, Pageable pageable);

    Page<InventoryListItemResponse> getLowStockItems(Pageable pageable);

    StockSummaryDto updateThreshold(UUID variantId, BigDecimal minimumQty, BigDecimal maximumQty, UUID actorUserId);

    void manualAdjustStock(UUID variantId, BigDecimal deltaQty, String reason, UUID actorUserId);

    StockDispatchResult issue(StockDispatchCommand command);

    Optional<StockSummaryDto> getStockSummary(UUID variantId);

    Map<UUID, StockSummaryDto> getStockSummaries(Collection<UUID> variantIds);

    Map<UUID, StockSummaryDto> getStockSummaries(Collection<UUID> variantIds, String warehouseCode);

    boolean checkAvailability(UUID variantId, BigDecimal requiredQty);

    void reserveStock(UUID variantId, BigDecimal qty);

    void releaseReservedStock(UUID variantId, BigDecimal qty);

    void adjustPhysicalStock(StockAdjustCommand command);

    void initializeMainWarehouseInventory(UUID variantId);

    VariantInventoryUsage getVariantDeletionUsage(UUID variantId);

    VariantInventoryUsage lockAndGetVariantDeletionUsage(UUID variantId);

    void deleteEmptyVariantInventory(UUID variantId);

    boolean hasDraftGoodsReceipt(UUID purchaseOrderId);

    boolean hasConfirmedGoodsReceipt(UUID purchaseOrderId);

    List<GoodsReceiptSummaryDto> getReceiptsByPurchaseOrderId(UUID purchaseOrderId);
}
