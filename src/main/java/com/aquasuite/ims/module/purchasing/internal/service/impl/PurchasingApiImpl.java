package com.aquasuite.ims.module.purchasing.internal.service.impl;

import com.aquasuite.ims.module.purchasing.api.PurchasingApi;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrder;
import com.aquasuite.ims.module.purchasing.internal.entity.PurchaseOrderItem;
import com.aquasuite.ims.module.purchasing.internal.enums.PurchaseOrderStatus;
import com.aquasuite.ims.module.purchasing.internal.error.PurchasingErrorCode;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderItemRepository;
import com.aquasuite.ims.module.purchasing.internal.repository.PurchaseOrderRepository;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PurchasingApiImpl implements PurchasingApi {

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderItemRepository purchaseOrderItemRepository;

    private static final List<PurchaseOrderStatus> OPEN_STATUSES = List.of(
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getIncomingStockQty(UUID variantId) {
        if (variantId == null) return BigDecimal.ZERO;
        List<PurchaseOrderItem> openItems = purchaseOrderItemRepository.findOpenItemsByVariantId(variantId, OPEN_STATUSES);
        return openItems.stream()
                .map(PurchaseOrderItem::getRemainingQty)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, BigDecimal> getIncomingStockQtyMap(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) return Collections.emptyMap();
        List<PurchaseOrderItem> openItems = purchaseOrderItemRepository.findOpenItemsByVariantIds(variantIds, OPEN_STATUSES);

        Map<UUID, BigDecimal> result = new HashMap<>();
        for (UUID vId : variantIds) {
            result.put(vId, BigDecimal.ZERO);
        }
        for (PurchaseOrderItem item : openItems) {
            result.merge(item.getVariantId(), item.getRemainingQty(), BigDecimal::add);
        }
        return result;
    }

    @Override
    @Transactional
    public void onGoodsReceiptConfirmed(UUID purchaseOrderId, Map<UUID, BigDecimal> receivedItems) {
        if (purchaseOrderId == null || receivedItems == null || receivedItems.isEmpty()) return;

        PurchaseOrder po = purchaseOrderRepository.findById(purchaseOrderId)
                .orElseThrow(() -> new AppException(PurchasingErrorCode.PO_NOT_FOUND));

        if (po.getStatus() != PurchaseOrderStatus.CONFIRMED && po.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED) {
            log.warn("PO {} is in status {}, ignoring receipt confirmation", po.getPoCode(), po.getStatus());
            return;
        }

        for (PurchaseOrderItem item : po.getItems()) {
            BigDecimal qtyReceivedNow = receivedItems.get(item.getVariantId());
            if (qtyReceivedNow != null && qtyReceivedNow.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentReceived = item.getQuantityReceived() != null ? item.getQuantityReceived() : BigDecimal.ZERO;
                item.setQuantityReceived(currentReceived.add(qtyReceivedNow));
            }
        }

        boolean allCompleted = po.getItems().stream()
                .allMatch(item -> item.getQuantityReceived() != null
                        && item.getQuantityReceived().compareTo(item.getQuantityOrdered()) >= 0);

        if (allCompleted) {
            po.setStatus(PurchaseOrderStatus.COMPLETED);
        } else {
            po.setStatus(PurchaseOrderStatus.PARTIALLY_RECEIVED);
        }

        purchaseOrderRepository.save(po);
        log.info("Updated PO {} status to {}", po.getPoCode(), po.getStatus());
    }
}