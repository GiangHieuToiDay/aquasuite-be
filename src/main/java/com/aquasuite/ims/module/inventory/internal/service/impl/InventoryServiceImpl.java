package com.aquasuite.ims.module.inventory.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.GoodsReceiptSummaryDto;
import com.aquasuite.ims.module.inventory.api.dto.MovementType;
import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.api.dto.VariantInventoryUsage;
import com.aquasuite.ims.module.inventory.api.event.InventoryThresholdChangedEvent;
import com.aquasuite.ims.module.inventory.api.event.LowStockAlertEvent;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.enums.InventoryStockStatus;
import com.aquasuite.ims.module.inventory.internal.error.InventoryErrorCode;
import com.aquasuite.ims.module.inventory.internal.payload.res.InventoryListItemResponse;
import com.aquasuite.ims.module.inventory.internal.repository.GoodsReceiptRepository;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockCountItemRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockMovementRepository;
import com.aquasuite.ims.module.inventory.internal.service.InventoryService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryApi, InventoryService {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamApi iamApi;
    private final CatalogApi catalogApi;

    // =========================================================================
    // 1. CỔNG FACADE PUBLIC DÀNH CHO CÁC MODULE KHÁC
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public Optional<StockSummaryDto> getStockSummary(UUID variantId) {
        if (variantId == null) return Optional.empty();
        return inventoryRepository.findByVariantId(variantId).map(this::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StockSummaryDto> getStockSummaries(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return Collections.emptyMap();
        return inventoryRepository.findByVariantIdIn(variantIds).stream()
                .map(this::toDto)
                .collect(Collectors.toMap(StockSummaryDto::variantId, Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkAvailability(UUID variantId, BigDecimal requiredQty) {
        if (requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0) return true;
        return inventoryRepository.findByVariantId(variantId)
                .map(inv -> inv.getAvailableQty().compareTo(requiredQty) >= 0)
                .orElse(false);
    }

    @Override
    @Transactional
    public void reserveStock(UUID variantId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;

        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        if (inv.getAvailableQty().compareTo(qty) < 0) {
            throw new AppException(InventoryErrorCode.INSUFFICIENT_AVAILABLE_STOCK, qty, inv.getAvailableQty());
        }

        BigDecimal currentReserved = inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
        inv.setReservedQty(currentReserved.add(qty));
        inventoryRepository.save(inv);

        if (inv.isLowStock()) {
            eventPublisher.publishEvent(new LowStockAlertEvent(
                    inv.getVariantId(), inv.getOnHandQty(), inv.getAvailableQty(), inv.getMinimumQty()
            ));
        }
    }

    @Override
    @Transactional
    public void releaseReservedStock(UUID variantId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;

        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        BigDecimal currentReserved = inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
        inv.setReservedQty(currentReserved.subtract(qty).max(BigDecimal.ZERO));
        inventoryRepository.save(inv);
    }

    @Override
    @Transactional
    public void adjustPhysicalStock(StockAdjustCommand command) {
        if (command == null || command.variantId() == null) return;
        BigDecimal deltaQty = command.deltaQty();
        if (deltaQty == null || deltaQty.compareTo(BigDecimal.ZERO) == 0) return;

        // 1. Lock dòng inventory của SKU để chống race condition
        Inventory inv = inventoryRepository.findByVariantIdForUpdate(command.variantId())
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, command.variantId()));

        BigDecimal currentOnHand = inv.getOnHandQty() != null ? inv.getOnHandQty() : BigDecimal.ZERO;
        BigDecimal newOnHand = currentOnHand.add(deltaQty);
        if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(InventoryErrorCode.NEGATIVE_ON_HAND_STOCK, currentOnHand, deltaQty);
        }

        // 2. Cập nhật tồn kho thực tế
        inv.setOnHandQty(newOnHand);

        // Nếu xuất kho từ đơn hàng đã đặt giữ trước đó -> giải phóng reservedQty
        if (command.releaseFromReserved() && deltaQty.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal dispatchedAbs = deltaQty.abs();
            BigDecimal currentReserved = inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
            inv.setReservedQty(currentReserved.subtract(dispatchedAbs).max(BigDecimal.ZERO));
        }
        inventoryRepository.save(inv);

        UUID actorId = command.actorUserId() != null ? command.actorUserId() : iamApi.getCurrentUserId();

        // 3. Ghi thẻ kho bất biến (stock_movements)
        StockMovement movement = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .inventoryId(inv.getInventoryId())
                .variantId(command.variantId())
                .movementType(command.movementType() != null ? MovementType.valueOf(command.movementType().name()) : MovementType.ADJUSTMENT_IN)
                .quantity(deltaQty)
                .balanceAfter(newOnHand)
                .referenceType(command.referenceType() != null ? command.referenceType() : "MANUAL_ADJUSTMENT")
                .referenceId(command.referenceId())
                .note(command.note())
                .createdBy(actorId)
                .build();
        stockMovementRepository.save(movement);

        // 4. Kiểm tra cảnh báo tồn tối thiểu
        if (inv.isLowStock()) {
            eventPublisher.publishEvent(new LowStockAlertEvent(
                    inv.getVariantId(), inv.getOnHandQty(), inv.getAvailableQty(), inv.getMinimumQty()
            ));
        }
    }

    @Override
    @Transactional
    public void initializeMainWarehouseInventory(UUID variantId) {
        if (variantId == null || inventoryRepository.findByVariantIdAndWarehouseCode(variantId, "WH_MAIN").isPresent()) return;
        Inventory inventory = Inventory.builder().variantId(variantId).warehouseCode("WH_MAIN")
                .onHandQty(BigDecimal.ZERO).reservedQty(BigDecimal.ZERO).minimumQty(BigDecimal.ZERO).build();
        inventoryRepository.save(inventory);
    }

    @Override
    @Transactional(readOnly = true)
    public VariantInventoryUsage getVariantDeletionUsage(UUID variantId) {
        return deletionUsage(variantId, inventoryRepository.findAllByVariantId(variantId));
    }

    @Override
    @Transactional
    public VariantInventoryUsage lockAndGetVariantDeletionUsage(UUID variantId) {
        return deletionUsage(variantId, inventoryRepository.findAllByVariantIdForUpdate(variantId));
    }

    private VariantInventoryUsage deletionUsage(UUID variantId, List<Inventory> inventories) {
        BigDecimal onHand = BigDecimal.ZERO;
        BigDecimal reserved = BigDecimal.ZERO;
        for (Inventory inventory : inventories) {
            onHand = onHand.add(inventory.getOnHandQty() == null ? BigDecimal.ZERO : inventory.getOnHandQty());
            reserved = reserved.add(inventory.getReservedQty() == null ? BigDecimal.ZERO : inventory.getReservedQty());
        }
        return new VariantInventoryUsage(variantId, onHand, reserved, stockMovementRepository.countByVariantId(variantId),
                stockCountItemRepository.countByVariantId(variantId));
    }

    @Override
    @Transactional
    public void deleteEmptyVariantInventory(UUID variantId) {
        List<Inventory> inventories = inventoryRepository.findAllByVariantIdForUpdate(variantId);
        boolean nonEmpty = inventories.stream().anyMatch(inventory ->
                (inventory.getOnHandQty() != null && inventory.getOnHandQty().signum() != 0)
                        || (inventory.getReservedQty() != null && inventory.getReservedQty().signum() != 0));
        if (nonEmpty || stockMovementRepository.countByVariantId(variantId) != 0 || stockCountItemRepository.countByVariantId(variantId) != 0) {
            throw new AppException(InventoryErrorCode.INVENTORY_NOT_EMPTY);
        }
        inventoryRepository.deleteByVariantId(variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasDraftGoodsReceipt(UUID purchaseOrderId) {
        if (purchaseOrderId == null) return false;
        return goodsReceiptRepository.existsByPurchaseOrderIdAndStatus(purchaseOrderId, "DRAFT");
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasConfirmedGoodsReceipt(UUID purchaseOrderId) {
        if (purchaseOrderId == null) return false;
        return goodsReceiptRepository.existsByPurchaseOrderIdAndStatus(purchaseOrderId, "CONFIRMED");
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptSummaryDto> getReceiptsByPurchaseOrderId(UUID purchaseOrderId) {
        if (purchaseOrderId == null) return Collections.emptyList();
        return goodsReceiptRepository.findByPurchaseOrderId(purchaseOrderId).stream()
                .map(gr -> GoodsReceiptSummaryDto.builder()
                        .goodsReceiptId(gr.getGoodsReceiptId())
                        .receiptCode(gr.getReceiptCode())
                        .purchaseOrderId(gr.getPurchaseOrderId())
                        .status(gr.getStatus())
                        .receiptDate(gr.getReceiptDate())
                        .supplierInvoiceNo(gr.getSupplierInvoiceNo())
                        .totalAmount(gr.getTotalAmount())
                        .note(gr.getNote())
                        .createdBy(gr.getCreatedBy())
                        .confirmedBy(gr.getConfirmedBy())
                        .confirmedAt(gr.getConfirmedAt())
                        .build())
                .toList();
    }

    // =========================================================================
    // 2. NỘI BỘ MODULE INVENTORY (INVENTORY SERVICE)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public StockSummaryDto getInventoryByVariantId(UUID variantId) {
        return getStockSummary(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovement> getMovementsByVariantId(UUID variantId, Pageable pageable) {
        return stockMovementRepository.findByVariantIdOrderByCreatedAtDesc(variantId, pageable);
    }

    @Override
    @Transactional
    public void manualAdjustStock(UUID variantId, BigDecimal deltaQty, String reason, UUID actorUserId) {
        if (deltaQty == null || deltaQty.compareTo(BigDecimal.ZERO) == 0) {
            throw new AppException(InventoryErrorCode.INVALID_ADJUST_QUANTITY);
        }
        MovementType type = deltaQty.compareTo(BigDecimal.ZERO) > 0
                ? MovementType.ADJUSTMENT_IN
                : MovementType.ADJUSTMENT_OUT;

        StockAdjustCommand command = StockAdjustCommand.builder()
                .variantId(variantId)
                .movementType(com.aquasuite.ims.module.inventory.api.dto.StockMovementType.valueOf(type.name()))
                .deltaQty(deltaQty)
                .releaseFromReserved(false)
                .referenceType("MANUAL_ADJUSTMENT")
                .referenceId(null)
                .note(reason)
                .actorUserId(actorUserId)
                .build();

        adjustPhysicalStock(command);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryListItemResponse> searchInventory(String keyword, String status, boolean lowStockOnly, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status);

        // Có từ khóa: phân giải tập variantId khớp (theo mã SKU hoặc tên sản phẩm) từ Catalog trước,
        // để việc phân trang vẫn chính xác ở tầng CSDL.
        if (StringUtils.hasText(keyword)) {
            Set<UUID> matchedVariantIds = resolveVariantIdsByKeyword(keyword.trim());
            if (matchedVariantIds.isEmpty()) {
                return Page.empty(pageable);
            }
            Page<Inventory> page = inventoryRepository.searchByVariantIds(matchedVariantIds, lowStockOnly, normalizedStatus, pageable);
            return enrich(page);
        }

        Page<Inventory> page = inventoryRepository.search(lowStockOnly, normalizedStatus, pageable);
        return enrich(page);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<InventoryListItemResponse> getLowStockItems(Pageable pageable) {
        // Danh sách tồn thấp toàn kho: tái dùng đúng định nghĩa low-stock trong câu truy vấn search.
        return enrich(inventoryRepository.search(true, null, pageable));
    }

    @Override
    @Transactional
    public StockSummaryDto updateThreshold(UUID variantId, BigDecimal minimumQty, BigDecimal maximumQty, UUID actorUserId) {
        validateThreshold(minimumQty, maximumQty);

        // Khóa dòng tồn để tránh ghi đè đồng thời; chỉ chỉnh ngưỡng, không đụng tồn vật lý/giữ hàng.
        Inventory inv = inventoryRepository.findByVariantIdForUpdate(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        BigDecimal oldMin = inv.getMinimumQty();
        BigDecimal oldMax = inv.getMaximumQty();

        inv.setMinimumQty(minimumQty);
        inv.setMaximumQty(maximumQty);
        inventoryRepository.save(inv);

        UUID actorId = actorUserId != null ? actorUserId : iamApi.getCurrentUserId();
        eventPublisher.publishEvent(new InventoryThresholdChangedEvent(
                actorId, variantId, oldMin, oldMax, minimumQty, maximumQty));

        // Ngưỡng thay đổi có thể khiến SKU rơi vào mức cảnh báo ngay lập tức.
        if (inv.isLowStock()) {
            eventPublisher.publishEvent(new LowStockAlertEvent(
                    inv.getVariantId(), inv.getOnHandQty(), inv.getAvailableQty(), inv.getMinimumQty()));
        }
        return toDto(inv);
    }

    /** Kiểm tra ràng buộc ngưỡng tồn: tối thiểu ≥ 0; tối đa (nếu có) ≥ tối thiểu. */
    private void validateThreshold(BigDecimal minimumQty, BigDecimal maximumQty) {
        if (minimumQty == null || minimumQty.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(InventoryErrorCode.INVALID_THRESHOLD);
        }
        if (maximumQty != null && maximumQty.compareTo(minimumQty) < 0) {
            throw new AppException(InventoryErrorCode.INVALID_THRESHOLD);
        }
    }

    /** Chuẩn hóa tham số trạng thái lọc; giá trị rỗng hoặc không hợp lệ được coi như không lọc (null). */
    private String normalizeStatus(String status) {
        if (!StringUtils.hasText(status)) return null;
        try {
            return InventoryStockStatus.valueOf(status.trim().toUpperCase(Locale.ROOT)).name();
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    /** Phân giải tập variantId có mã SKU hoặc tên sản phẩm/biến thể chứa từ khóa (không phân biệt hoa thường). */
    private Set<UUID> resolveVariantIdsByKeyword(String keyword) {
        String needle = keyword.toLowerCase(Locale.ROOT);
        return catalogApi.getAllActiveSkus().stream()
                .filter(sku -> contains(sku.sku(), needle)
                        || contains(sku.productName(), needle)
                        || contains(sku.variantName(), needle))
                .map(CatalogSkuSummaryDto::variantId)
                .collect(Collectors.toSet());
    }

    private boolean contains(String value, String lowerNeedle) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(lowerNeedle);
    }

    /** Gắn thông tin SKU (mã, tên, đơn vị) từ Catalog vào trang tồn kho để trả về cho màn tra cứu. */
    private Page<InventoryListItemResponse> enrich(Page<Inventory> page) {
        List<UUID> variantIds = page.getContent().stream().map(Inventory::getVariantId).toList();
        Map<UUID, CatalogSkuSummaryDto> skuMap = variantIds.isEmpty()
                ? Collections.emptyMap()
                : catalogApi.getSkuSummaries(variantIds);
        return page.map(inv -> toListItem(inv, skuMap.get(inv.getVariantId())));
    }

    private InventoryListItemResponse toListItem(Inventory inv, CatalogSkuSummaryDto sku) {
        String productName = sku == null ? null
                : (sku.productName() != null ? sku.productName() : sku.variantName());
        return InventoryListItemResponse.builder()
                .variantId(inv.getVariantId())
                .sku(sku != null ? sku.sku() : null)
                .productName(productName)
                .unit(sku != null ? sku.unit() : null)
                .onHandQty(inv.getOnHandQty())
                .reservedQty(inv.getReservedQty())
                .availableQty(inv.getAvailableQty())
                .minimumQty(inv.getMinimumQty())
                .maximumQty(inv.getMaximumQty())
                .status(computeStatus(inv))
                .lowStock(inv.isLowStock())
                .build();
    }

    /** Suy ra trạng thái tồn kho hiển thị từ tồn khả dụng và ngưỡng tối thiểu. */
    private InventoryStockStatus computeStatus(Inventory inv) {
        if (inv.getAvailableQty().compareTo(BigDecimal.ZERO) <= 0) {
            return InventoryStockStatus.OUT_OF_STOCK;
        }
        if (inv.isLowStock()) {
            return InventoryStockStatus.LOW_STOCK;
        }
        return InventoryStockStatus.IN_STOCK;
    }

    private StockSummaryDto toDto(Inventory inv) {
        return StockSummaryDto.builder()
                .inventoryId(inv.getInventoryId())
                .variantId(inv.getVariantId())
                .warehouseCode(inv.getWarehouseCode())
                .onHandQty(inv.getOnHandQty())
                .reservedQty(inv.getReservedQty())
                .availableQty(inv.getAvailableQty())
                .minimumQty(inv.getMinimumQty())
                .maximumQty(inv.getMaximumQty())
                .isLowStock(inv.isLowStock())
                .build();
    }
}