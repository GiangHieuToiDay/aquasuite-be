package com.aquasuite.ims.module.inventory.internal.service.impl;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.MovementType;
import com.aquasuite.ims.module.inventory.api.dto.StockAdjustCommand;
import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.entity.StockCount;
import com.aquasuite.ims.module.inventory.internal.entity.StockCountItem;
import com.aquasuite.ims.module.inventory.internal.enums.StockCountStatus;
import com.aquasuite.ims.module.inventory.internal.error.InventoryErrorCode;
import com.aquasuite.ims.module.inventory.internal.payload.req.CreateStockCountRequest;
import com.aquasuite.ims.module.inventory.internal.payload.req.StockCountItemRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.StockCountItemResponse;
import com.aquasuite.ims.module.inventory.internal.payload.res.StockCountResponse;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockCountItemRepository;
import com.aquasuite.ims.module.inventory.internal.repository.StockCountRepository;
import com.aquasuite.ims.module.inventory.internal.service.StockCountService;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
@Service
@RequiredArgsConstructor
public class StockCountServiceImpl implements StockCountService {

    private final StockCountRepository stockCountRepository;
    private final StockCountItemRepository stockCountItemRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryApi inventoryApi;
    private final IamApi iamApi;

    /**
     * 1. Tạo phiếu kiểm kho dạng nháp (DRAFT)
     */
    @Override
    @Transactional
    public StockCountResponse createDraftStockCount(CreateStockCountRequest request, UUID actorUserId) {
        UUID userId = actorUserId != null ? actorUserId : iamApi.getCurrentUserId();
        String warehouse = (request.warehouseCode() != null && !request.warehouseCode().isBlank())
                ? request.warehouseCode().trim()
                : "WH_MAIN";

        String code = generateStockCountCode();

        StockCount stockCount = StockCount.builder()
                .stockCountCode(code)
                .warehouseCode(warehouse)
                .status(StockCountStatus.DRAFT)
                .countedBy(userId)
                .note(request.note())
                .build();
        stockCount = stockCountRepository.save(stockCount);

        List<StockCountItemResponse> itemResponses = new ArrayList<>();

        if (request.items() != null) {
            Set<UUID> seenVariants = new HashSet<>();
            for (StockCountItemRequest itemReq : request.items()) {
                if (!seenVariants.add(itemReq.variantId())) {
                    throw new AppException(InventoryErrorCode.DUPLICATE_STOCK_COUNT_ITEM, itemReq.variantId());
                }
            }

            for (StockCountItemRequest itemReq : request.items()) {
                Inventory inv = inventoryRepository.findByVariantIdAndWarehouseCode(itemReq.variantId(), warehouse)
                        .orElseGet(() -> {
                            // Nếu chưa có dòng inventory cho variant này -> tạo mới với số tồn 0
                            Inventory newInv = Inventory.builder()
                                    .inventoryId(UUID.randomUUID())
                                    .variantId(itemReq.variantId())
                                    .warehouseCode(warehouse)
                                    .onHandQty(BigDecimal.ZERO)
                                    .reservedQty(BigDecimal.ZERO)
                                    .minimumQty(BigDecimal.ZERO)
                                    .build();
                            return inventoryRepository.save(newInv);
                        });

                BigDecimal sysQty = inv.getOnHandQty() != null ? inv.getOnHandQty() : BigDecimal.ZERO;
                BigDecimal actQty = itemReq.actualQty() != null ? itemReq.actualQty() : BigDecimal.ZERO;
                BigDecimal diffQty = actQty.subtract(sysQty);

                StockCountItem item = StockCountItem.builder()
                        .stockCountId(stockCount.getStockCountId())
                        .inventoryId(inv.getInventoryId())
                        .variantId(itemReq.variantId())
                        .systemQty(sysQty)
                        .actualQty(actQty)
                        .differenceQty(diffQty)
                        .reason(itemReq.reason())
                        .build();
                item = stockCountItemRepository.save(item);

                itemResponses.add(StockCountItemResponse.builder()
                        .stockCountItemId(item.getStockCountItemId())
                        .inventoryId(item.getInventoryId())
                        .variantId(item.getVariantId())
                        .systemQty(item.getSystemQty())
                        .actualQty(item.getActualQty())
                        .differenceQty(item.getDifferenceQty())
                        .reason(item.getReason())
                        .build());
            }
        }

        return mapToResponse(stockCount, itemResponses);
    }

    /**
     * 2. Duyệt phiếu kiểm kho (APPROVE) -> Cân bằng tồn kho và ghi thẻ kho
     */
    @Override
    @Transactional
    public StockCountResponse approveStockCount(UUID stockCountId, UUID approverUserId) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.STOCK_COUNT_NOT_FOUND, stockCountId));

        if (stockCount.getStatus() != StockCountStatus.DRAFT && stockCount.getStatus() != StockCountStatus.PENDING_APPROVAL) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_COUNT_STATUS, stockCount.getStatus());
        }

        UUID approverId = approverUserId != null ? approverUserId : iamApi.getCurrentUserId();

        stockCount.setStatus(StockCountStatus.COMPLETED);
        stockCount.setApprovedBy(approverId);
        stockCount.setCountDate(LocalDateTime.now());
        stockCountRepository.save(stockCount);

        List<StockCountItem> items = stockCountItemRepository.findByStockCountId(stockCountId);
        List<StockCountItemResponse> itemResponses = new ArrayList<>(items.size());

        for (StockCountItem item : items) {
            BigDecimal diff = item.getDifferenceQty();
            if (diff != null && diff.compareTo(BigDecimal.ZERO) != 0) {
                MovementType movementType = diff.compareTo(BigDecimal.ZERO) > 0
                        ? MovementType.ADJUSTMENT_IN
                        : MovementType.ADJUSTMENT_OUT;

                StockAdjustCommand command = StockAdjustCommand.builder()
                        .variantId(item.getVariantId())
                        .movementType(com.aquasuite.ims.module.inventory.api.dto.StockMovementType.valueOf(movementType.name()))
                        .deltaQty(diff)
                        .releaseFromReserved(false)
                        .referenceType("STOCK_COUNT")
                        .referenceId(stockCount.getStockCountCode())
                        .note("Cân bằng kho theo phiếu kiểm kê [" + stockCount.getStockCountCode() + "]"
                                + (item.getReason() != null ? " - Lý do: " + item.getReason() : ""))
                        .actorUserId(approverId)
                        .build();

                inventoryApi.adjustPhysicalStock(command);
            }

            itemResponses.add(StockCountItemResponse.builder()
                    .stockCountItemId(item.getStockCountItemId())
                    .inventoryId(item.getInventoryId())
                    .variantId(item.getVariantId())
                    .systemQty(item.getSystemQty())
                    .actualQty(item.getActualQty())
                    .differenceQty(item.getDifferenceQty())
                    .reason(item.getReason())
                    .build());
        }

        return mapToResponse(stockCount, itemResponses);
    }

    /**
     * 3. Hủy phiếu kiểm kho
     */
    @Override
    @Transactional
    public StockCountResponse cancelStockCount(UUID stockCountId) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.STOCK_COUNT_NOT_FOUND, stockCountId));

        if (stockCount.getStatus() == StockCountStatus.COMPLETED) {
            throw new AppException(InventoryErrorCode.INVALID_STOCK_COUNT_STATUS, "Phiếu đã duyệt hoàn tất, không thể hủy");
        }

        stockCount.setStatus(StockCountStatus.CANCELLED);
        stockCountRepository.save(stockCount);

        List<StockCountItem> items = stockCountItemRepository.findByStockCountId(stockCountId);
        List<StockCountItemResponse> itemResponses = items.stream()
                .map(item -> StockCountItemResponse.builder()
                        .stockCountItemId(item.getStockCountItemId())
                        .inventoryId(item.getInventoryId())
                        .variantId(item.getVariantId())
                        .systemQty(item.getSystemQty())
                        .actualQty(item.getActualQty())
                        .differenceQty(item.getDifferenceQty())
                        .reason(item.getReason())
                        .build())
                .toList();

        return mapToResponse(stockCount, itemResponses);
    }

    /**
     * 4. Chi tiết phiếu kiểm kho
     */
    @Override
    @Transactional(readOnly = true)
    public StockCountResponse getStockCountDetail(UUID stockCountId) {
        StockCount stockCount = stockCountRepository.findById(stockCountId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.STOCK_COUNT_NOT_FOUND, stockCountId));

        List<StockCountItem> items = stockCountItemRepository.findByStockCountId(stockCountId);
        List<StockCountItemResponse> itemResponses = items.stream()
                .map(item -> StockCountItemResponse.builder()
                        .stockCountItemId(item.getStockCountItemId())
                        .inventoryId(item.getInventoryId())
                        .variantId(item.getVariantId())
                        .systemQty(item.getSystemQty())
                        .actualQty(item.getActualQty())
                        .differenceQty(item.getDifferenceQty())
                        .reason(item.getReason())
                        .build())
                .toList();

        return mapToResponse(stockCount, itemResponses);
    }

    /**
     * 5. Danh sách phiếu kiểm kho phân trang
     */
    @Override
    @Transactional(readOnly = true)
    public Page<StockCountResponse> listStockCounts(Pageable pageable) {
        return stockCountRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(sc -> mapToResponse(sc, null));
    }

    private StockCountResponse mapToResponse(StockCount sc, List<StockCountItemResponse> items) {
        return StockCountResponse.builder()
                .stockCountId(sc.getStockCountId())
                .stockCountCode(sc.getStockCountCode())
                .warehouseCode(sc.getWarehouseCode())
                .status(sc.getStatus())
                .countedBy(sc.getCountedBy())
                .approvedBy(sc.getApprovedBy())
                .countDate(sc.getCountDate())
                .note(sc.getNote())
                .createdAt(sc.getCreatedAt())
                .items(items)
                .build();
    }

    private String generateStockCountCode() {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyMMddHHmm"));
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "STC" + timestamp + rand;
    }
}
