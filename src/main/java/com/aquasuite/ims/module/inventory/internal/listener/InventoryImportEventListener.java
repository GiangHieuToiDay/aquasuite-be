package com.aquasuite.ims.module.inventory.internal.listener;

import com.aquasuite.ims.module.catalog.events.VariantsBulkImportedEvent;
import com.aquasuite.ims.module.catalog.events.VariantsBulkImportedEvent.InitialStockItem;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.entity.StockMovement;
import com.aquasuite.ims.module.inventory.internal.entity.enums.MovementType;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class InventoryImportEventListener {

    private static final int BATCH_SIZE = 100;
    private static final String DEFAULT_WAREHOUSE = "WH_MAIN";

    @PersistenceContext
    private final EntityManager entityManager;

    private final InventoryRepository inventoryRepository;

    @Async("taskExecutor")
    @EventListener
    @Transactional
    public void handleVariantsBulkImported(VariantsBulkImportedEvent event) {
        if (event == null || event.items() == null || event.items().isEmpty()) {
            return;
        }

        List<InitialStockItem> items = event.items();
        log.info("[Inventory] Received VariantsBulkImportedEvent for jobId: {} with {} items",
                event.jobId(), items.size());

        // Chia thành từng mẻ 100 items để tối ưu JDBC Batch & tránh tràn RAM
        for (int i = 0; i < items.size(); i += BATCH_SIZE) {
            List<InitialStockItem> batch = items.subList(i, Math.min(i + BATCH_SIZE, items.size()));
            processSingleStockBatch(batch, event.jobId());
        }

        log.info("[Inventory] Successfully initialized stock and ledger for jobId: {}", event.jobId());
    }

    private void processSingleStockBatch(List<InitialStockItem> batch, String jobId) {
        // 1. Kiểm tra chống trùng lặp: Lấy các variantId đã có sẵn trong bảng inventories (nếu có)
        Set<UUID> variantIds = batch.stream().map(InitialStockItem::variantId).collect(Collectors.toSet());
        Set<UUID> existingVariantIds = inventoryRepository.findExistingVariantIds(variantIds);

        for (InitialStockItem item : batch) {
            if (existingVariantIds != null && existingVariantIds.contains(item.variantId())) {
                log.debug("Variant {} already has an inventory record. Skipping initialization.", item.sku());
                continue;
            }

            BigDecimal initialStock = item.initialStock() != null ? item.initialStock() : BigDecimal.ZERO;
            BigDecimal minQty = item.minimumQty() != null ? item.minimumQty() : BigDecimal.ZERO;
            UUID newInventoryId = UUID.randomUUID();

            // Nhiệm vụ 1: Khởi tạo bản ghi bảng 16. inventories
            Inventory inventory = Inventory.builder()
                    .inventoryId(newInventoryId)
                    .variantId(item.variantId())
                    .warehouseCode(DEFAULT_WAREHOUSE)
                    .onHandQty(initialStock)
                    .reservedQty(BigDecimal.ZERO)
                    .minimumQty(minQty)
                    .build();
            entityManager.persist(inventory);

            // Nhiệm vụ 2: Chỉ ghi thẻ kho 17. stock_movements nếu initialStock > 0
            if (initialStock.compareTo(BigDecimal.ZERO) > 0) {
                StockMovement movement = StockMovement.builder()
                        .movementId(UUID.randomUUID())
                        .inventoryId(newInventoryId)
                        .variantId(item.variantId())
                        .movementType(MovementType.INITIAL_IMPORT)
                        .quantity(initialStock)
                        .balanceAfter(initialStock)
                        .referenceType("EXCEL_IMPORT")
                        .referenceId(jobId)
                        .note("Khởi tạo tồn kho đầu kỳ từ file Excel [Job: " + jobId + "] - SKU: " + item.sku())
                        .build();
                entityManager.persist(movement);
            }
        }

        // Đẩy batch xuống DB và giải phóng Persistence Context
        entityManager.flush();
        entityManager.clear();
    }
}
