package com.aquasuite.ims.module.inventory.internal.service.impl;

import java.util.UUID;

import com.aquasuite.ims.module.iam.api.IamFacade;
import com.aquasuite.ims.module.inventory.InventoryFacade;
import com.aquasuite.ims.module.inventory.api.InventoryAvailabilityApi;
import com.aquasuite.ims.module.inventory.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.dto.StockSummaryDto;
import com.aquasuite.ims.module.inventory.events.LowStockAlertEvent;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.entity.enums.MovementType;
import com.aquasuite.ims.module.inventory.internal.exception.InventoryErrorCode;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
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
import org.springframework.transaction.annotation.Propagation;

import java.math.BigDecimal;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl
        implements InventoryFacade, InventoryService, InventoryAvailabilityApi {

    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final IamFacade iamFacade;

    // =========================================================================
    // 1. CỔNG FACADE PUBLIC DÀNH CHO CÁC MODULE KHÁC
    // =========================================================================

    /**
     * Tái sử dụng hàm đọc tồn theo lô, không gọi IAM riêng hoặc thay đổi tồn kho.
     * Transaction đọc độc lập giúp Catalog vẫn trả preview UNKNOWN khi truy vấn tồn lỗi,
     * thay vì bị đánh dấu rollback theo transaction lỗi của Inventory.
     */
    @Override
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<UUID, BigDecimal> availableQuantities(Collection<UUID> variantIds) {
        return getStockSummaries(variantIds).values().stream()
                .collect(
                        Collectors.toMap(
                                StockSummaryDto::variantId, StockSummaryDto::availableQty));
    }

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
                .collect(
                        Collectors.toMap(
                                StockSummaryDto::variantId, Function.identity(), (a, b) -> a));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean checkAvailability(UUID variantId, BigDecimal requiredQty) {
        if (requiredQty == null || requiredQty.compareTo(BigDecimal.ZERO) <= 0) return true;
        return inventoryRepository
                .findByVariantId(variantId)
                .map(inv -> inv.getAvailableQty().compareTo(requiredQty) >= 0)
                .orElse(false);
    }

    @Override
    @Transactional
    public void reserveStock(UUID variantId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;

        Inventory inv =
                inventoryRepository
                        .findByVariantIdForUpdate(variantId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        if (inv.getAvailableQty().compareTo(qty) < 0) {
            throw new AppException(
                    InventoryErrorCode.INSUFFICIENT_AVAILABLE_STOCK, qty, inv.getAvailableQty());
        }

        BigDecimal currentReserved =
                inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
        inv.setReservedQty(currentReserved.add(qty));
        inventoryRepository.save(inv);

        if (inv.isLowStock()) {
            eventPublisher.publishEvent(
                    new LowStockAlertEvent(
                            inv.getVariantId(),
                            inv.getOnHandQty(),
                            inv.getAvailableQty(),
                            inv.getMinimumQty()));
        }
    }

    @Override
    @Transactional
    public void releaseReservedStock(UUID variantId, BigDecimal qty) {
        if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) return;

        Inventory inv =
                inventoryRepository
                        .findByVariantIdForUpdate(variantId)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        BigDecimal currentReserved =
                inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
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
        Inventory inv =
                inventoryRepository
                        .findByVariantIdForUpdate(command.variantId())
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                InventoryErrorCode.INVENTORY_NOT_FOUND,
                                                command.variantId()));

        BigDecimal currentOnHand =
                inv.getOnHandQty() != null ? inv.getOnHandQty() : BigDecimal.ZERO;
        BigDecimal newOnHand = currentOnHand.add(deltaQty);
        if (newOnHand.compareTo(BigDecimal.ZERO) < 0) {
            throw new AppException(
                    InventoryErrorCode.NEGATIVE_ON_HAND_STOCK, currentOnHand, deltaQty);
        }

        // 2. Cập nhật tồn kho thực tế
        inv.setOnHandQty(newOnHand);

        // Nếu xuất kho từ đơn hàng đã đặt giữ trước đó -> giải phóng reservedQty
        if (command.releaseFromReserved() && deltaQty.compareTo(BigDecimal.ZERO) < 0) {
            BigDecimal dispatchedAbs = deltaQty.abs();
            BigDecimal currentReserved =
                    inv.getReservedQty() != null ? inv.getReservedQty() : BigDecimal.ZERO;
            inv.setReservedQty(currentReserved.subtract(dispatchedAbs).max(BigDecimal.ZERO));
        }
        inventoryRepository.save(inv);

        UUID actorId =
                command.actorUserId() != null
                        ? command.actorUserId()
                        : iamFacade.getCurrentUserId();

        // 3. Ghi thẻ kho bất biến (stock_movements)
        StockMovement movement =
                StockMovement.builder()
                        .movementId(UUID.randomUUID())
                        .inventoryId(inv.getInventoryId())
                        .variantId(command.variantId())
                        .movementType(
                                command.movementType() != null
                                        ? command.movementType()
                                        : MovementType.ADJUSTMENT_IN)
                        .quantity(deltaQty)
                        .balanceAfter(newOnHand)
                        .referenceType(
                                command.referenceType() != null
                                        ? command.referenceType()
                                        : "MANUAL_ADJUSTMENT")
                        .referenceId(command.referenceId())
                        .note(command.note())
                        .createdBy(actorId)
                        .build();
        stockMovementRepository.save(movement);

        // 4. Kiểm tra cảnh báo tồn tối thiểu
        if (inv.isLowStock()) {
            eventPublisher.publishEvent(
                    new LowStockAlertEvent(
                            inv.getVariantId(),
                            inv.getOnHandQty(),
                            inv.getAvailableQty(),
                            inv.getMinimumQty()));
        }
    }

    // =========================================================================
    // 2. NỘI BỘ MODULE INVENTORY (INVENTORY SERVICE)
    // =========================================================================

    @Override
    @Transactional(readOnly = true)
    public StockSummaryDto getInventoryByVariantId(UUID variantId) {
        return getStockSummary(variantId)
                .orElseThrow(
                        () -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<StockMovement> getMovementsByVariantId(UUID variantId, Pageable pageable) {
        return stockMovementRepository.findByVariantIdOrderByCreatedAtDesc(variantId, pageable);
    }

    @Override
    @Transactional
    public void manualAdjustStock(
            UUID variantId, BigDecimal deltaQty, String reason, UUID actorUserId) {
        if (deltaQty == null || deltaQty.compareTo(BigDecimal.ZERO) == 0) {
            throw new AppException(InventoryErrorCode.INVALID_ADJUST_QUANTITY);
        }
        MovementType type =
                deltaQty.compareTo(BigDecimal.ZERO) > 0
                        ? MovementType.ADJUSTMENT_IN
                        : MovementType.ADJUSTMENT_OUT;

        StockAdjustCommand command =
                StockAdjustCommand.builder()
                        .variantId(variantId)
                        .movementType(type)
                        .deltaQty(deltaQty)
                        .releaseFromReserved(false)
                        .referenceType("MANUAL_ADJUSTMENT")
                        .referenceId(null)
                        .note(reason)
                        .actorUserId(actorUserId)
                        .build();

        adjustPhysicalStock(command);
    }

    private StockSummaryDto toDto(Inventory inv) {
        return StockSummaryDto.builder()
                .variantId(inv.getVariantId())
                .warehouseCode(inv.getWarehouseCode())
                .onHandQty(inv.getOnHandQty())
                .reservedQty(inv.getReservedQty())
                .availableQty(inv.getAvailableQty())
                .minimumQty(inv.getMinimumQty())
                .build();
    }
}
