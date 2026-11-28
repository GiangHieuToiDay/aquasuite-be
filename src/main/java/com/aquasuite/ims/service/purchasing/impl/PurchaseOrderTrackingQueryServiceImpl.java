package com.aquasuite.ims.service.purchasing.impl;

import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderBriefView;
import com.aquasuite.ims.dto.purchasing.response.VariantPurchaseOrderLineView;
import com.aquasuite.ims.entity.inventory.Supplier;
import com.aquasuite.ims.entity.purchasing.PurchaseOrder;
import com.aquasuite.ims.entity.purchasing.PurchaseOrderItem;
import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import com.aquasuite.ims.repository.inventory.SupplierRepository;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderItemRepository;
import com.aquasuite.ims.repository.purchasing.PurchaseOrderRepository;
import com.aquasuite.ims.service.purchasing.PurchaseOrderTrackingQueryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PurchaseOrderTrackingQueryServiceImpl implements PurchaseOrderTrackingQueryService {

    private static final List<PurchaseOrderStatus> OPEN_STATUSES = List.of(
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED
    );

    private static final List<PurchaseOrderStatus> TRACKABLE_STATUSES = List.of(
            PurchaseOrderStatus.CONFIRMED,
            PurchaseOrderStatus.PARTIALLY_RECEIVED,
            PurchaseOrderStatus.COMPLETED
    );

    private final PurchaseOrderItemRepository purchaseOrderItemRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final SupplierRepository supplierRepository;

    @Override
    @Transactional(readOnly = true)
    public List<VariantPurchaseOrderLineView> findLinesByVariantIds(Collection<UUID> variantIds, boolean includeCompleted) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyList();
        }
        List<PurchaseOrderStatus> statuses = includeCompleted ? TRACKABLE_STATUSES : OPEN_STATUSES;
        List<PurchaseOrderItem> items = purchaseOrderItemRepository
                .findItemsWithOrderByVariantIdsAndStatuses(variantIds, statuses);
        return mapLines(items);
    }

    @Override
    @Transactional(readOnly = true)
    public List<VariantPurchaseOrderLineView> findAllOpenLines() {
        List<PurchaseOrderItem> items = purchaseOrderItemRepository.findAllItemsWithOrderByStatuses(OPEN_STATUSES);
        return mapLines(items);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, PurchaseOrderBriefView> findOrderBriefsByIds(Collection<UUID> purchaseOrderIds) {
        if (purchaseOrderIds == null || purchaseOrderIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<PurchaseOrder> orders = purchaseOrderRepository.findAllById(purchaseOrderIds);
        if (orders.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, Supplier> supplierMap = loadSuppliers(
                orders.stream().map(PurchaseOrder::getSupplierId).filter(Objects::nonNull).collect(Collectors.toSet()));

        return orders.stream()
                .map(po -> toBrief(po, supplierMap.get(po.getSupplierId())))
                .collect(Collectors.toMap(PurchaseOrderBriefView::purchaseOrderId, Function.identity(), (a, b) -> a));
    }

    private List<VariantPurchaseOrderLineView> mapLines(List<PurchaseOrderItem> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        Set<UUID> supplierIds = items.stream()
                .map(item -> item.getPurchaseOrder().getSupplierId())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, Supplier> supplierMap = loadSuppliers(supplierIds);

        Comparator<VariantPurchaseOrderLineView> comparator = Comparator
                .comparing((VariantPurchaseOrderLineView v) -> isOpenStatus(v.status()) ? 0 : 1)
                .thenComparing(VariantPurchaseOrderLineView::expectedDate, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(VariantPurchaseOrderLineView::orderDate, Comparator.nullsLast(Comparator.reverseOrder()));

        return items.stream()
                .map(item -> toLineView(item, supplierMap.get(item.getPurchaseOrder().getSupplierId())))
                .sorted(comparator)
                .toList();
    }

    private Map<UUID, Supplier> loadSuppliers(Set<UUID> supplierIds) {
        if (supplierIds == null || supplierIds.isEmpty()) {
            return Collections.emptyMap();
        }
        return supplierRepository.findAllById(supplierIds).stream()
                .collect(Collectors.toMap(Supplier::getSupplierId, Function.identity(), (a, b) -> a));
    }

    private VariantPurchaseOrderLineView toLineView(PurchaseOrderItem item, Supplier supplier) {
        PurchaseOrder po = item.getPurchaseOrder();
        BigDecimal ordered = item.getQuantityOrdered() != null ? item.getQuantityOrdered() : BigDecimal.ZERO;
        BigDecimal received = item.getQuantityReceived() != null ? item.getQuantityReceived() : BigDecimal.ZERO;
        BigDecimal remaining = item.getRemainingQty();
        String statusName = po.getStatus() != null ? po.getStatus().name() : null;

        return VariantPurchaseOrderLineView.builder()
                .purchaseOrderId(po.getPurchaseOrderId())
                .purchaseOrderItemId(item.getPurchaseOrderItemId())
                .poCode(po.getPoCode())
                .status(statusName)
                .statusLabel(toVietnamesePoStatus(po.getStatus()))
                .supplierId(po.getSupplierId())
                .supplierCode(supplier != null ? supplier.getSupplierCode() : null)
                .supplierName(supplier != null ? supplier.getSupplierName() : null)
                .warehouseCode(po.getWarehouseCode())
                .orderDate(po.getOrderDate())
                .expectedDate(po.getExpectedDate())
                .variantId(item.getVariantId())
                .unit(item.getUnit())
                .orderedQty(ordered)
                .receivedQty(received)
                .remainingQty(remaining)
                .unitCost(item.getUnitCost())
                .build();
    }

    private PurchaseOrderBriefView toBrief(PurchaseOrder po, Supplier supplier) {
        return PurchaseOrderBriefView.builder()
                .purchaseOrderId(po.getPurchaseOrderId())
                .poCode(po.getPoCode())
                .status(po.getStatus() != null ? po.getStatus().name() : null)
                .statusLabel(toVietnamesePoStatus(po.getStatus()))
                .supplierId(po.getSupplierId())
                .supplierCode(supplier != null ? supplier.getSupplierCode() : null)
                .supplierName(supplier != null ? supplier.getSupplierName() : null)
                .orderDate(po.getOrderDate())
                .expectedDate(po.getExpectedDate())
                .build();
    }

    private static boolean isOpenStatus(String status) {
        return PurchaseOrderStatus.CONFIRMED.name().equals(status)
                || PurchaseOrderStatus.PARTIALLY_RECEIVED.name().equals(status);
    }

    public static String toVietnamesePoStatus(PurchaseOrderStatus status) {
        if (status == null) return "Không xác định";
        return switch (status) {
            case DRAFT -> "Bản nháp";
            case CONFIRMED -> "Đã xác nhận";
            case PARTIALLY_RECEIVED -> "Nhận một phần";
            case COMPLETED -> "Hoàn thành";
            case CANCELLED -> "Đã hủy";
        };
    }
}
