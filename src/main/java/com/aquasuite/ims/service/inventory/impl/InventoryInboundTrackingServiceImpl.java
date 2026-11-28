package com.aquasuite.ims.service.inventory.impl;

import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.dto.inventory.response.InventoryInboundKpiResponse;
import com.aquasuite.ims.dto.inventory.response.ReadableStockMovementResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse.PurchaseOrderTrackingItem;
import com.aquasuite.ims.dto.inventory.response.VariantInboundDetailResponse.VariantGoodsReceiptBrief;
import com.aquasuite.ims.dto.inventory.response.VariantInboundSummaryResponse;
import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderBriefView;
import com.aquasuite.ims.dto.purchasing.response.VariantPurchaseOrderLineView;
import com.aquasuite.ims.entity.inventory.GoodsReceipt;
import com.aquasuite.ims.entity.inventory.GoodsReceiptItem;
import com.aquasuite.ims.entity.inventory.Inventory;
import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.enums.inventory.MovementType;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.inventory.InventoryErrorCode;
import com.aquasuite.ims.repository.inventory.GoodsReceiptItemRepository;
import com.aquasuite.ims.repository.inventory.GoodsReceiptRepository;
import com.aquasuite.ims.repository.inventory.InventoryRepository;
import com.aquasuite.ims.repository.inventory.StockMovementRepository;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.InventoryInboundTrackingService;
import com.aquasuite.ims.service.purchasing.PurchaseOrderTrackingQueryService;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.util.PageableUtils;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryInboundTrackingServiceImpl implements InventoryInboundTrackingService {

    private static final int MAX_SUMMARY_BATCH_SIZE = 100;
    private static final String MAIN_WAREHOUSE = "WH_MAIN";

    private final InventoryRepository inventoryRepository;
    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptItemRepository goodsReceiptItemRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderTrackingQueryService poTrackingQueryService;
    private final SalesQueryService salesQueryService;
    private final IamQueryService iamApi;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public InventoryInboundKpiResponse getKpiOverview() {
        List<Inventory> allInventories = inventoryRepository.findAll().stream()
                .filter(inv -> MAIN_WAREHOUSE.equals(inv.getWarehouseCode()))
                .toList();

        List<VariantPurchaseOrderLineView> openLines = poTrackingQueryService.findAllOpenLines();
        LocalDate today = LocalDate.now();

        Map<UUID, BigDecimal> incomingByVariant = new HashMap<>();
        Set<UUID> openPoIds = new HashSet<>();
        Set<UUID> overduePoIds = new HashSet<>();

        for (VariantPurchaseOrderLineView line : openLines) {
            BigDecimal rem = safeQty(line.remainingQty());
            if (rem.compareTo(BigDecimal.ZERO) > 0) {
                incomingByVariant.merge(line.variantId(), rem, BigDecimal::add);
                openPoIds.add(line.purchaseOrderId());
                if (line.expectedDate() != null && line.expectedDate().isBefore(today)) {
                    overduePoIds.add(line.purchaseOrderId());
                }
            }
        }

        long needOrderCount = 0;
        long reservedSkuCount = 0;
        BigDecimal totalReservedQty = BigDecimal.ZERO;

        for (Inventory inv : allInventories) {
            BigDecimal reserved = safeQty(inv.getReservedQty());
            if (reserved.compareTo(BigDecimal.ZERO) > 0) {
                reservedSkuCount++;
                totalReservedQty = totalReservedQty.add(reserved);
            }
            BigDecimal incoming = incomingByVariant.getOrDefault(inv.getVariantId(), BigDecimal.ZERO);
            String hint = computeRestockHint(inv, incoming);
            if ("NEED_ORDER".equals(hint) || "INCOMING_PARTIAL".equals(hint)) {
                needOrderCount++;
            }
        }

        BigDecimal totalIncomingQty = incomingByVariant.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return InventoryInboundKpiResponse.builder()
                .totalSkus(allInventories.size())
                .needOrderCount(needOrderCount)
                .incomingSkuCount(incomingByVariant.size())
                .totalIncomingQty(totalIncomingQty)
                .openPoCount(openPoIds.size())
                .overduePoCount(overduePoIds.size())
                .reservedSkuCount(reservedSkuCount)
                .totalReservedQty(totalReservedQty)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, VariantInboundSummaryResponse> getInboundSummaries(Collection<UUID> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Set<UUID> uniqueIds = variantIds.stream().filter(Objects::nonNull).collect(Collectors.toSet());
        if (uniqueIds.isEmpty()) {
            return Collections.emptyMap();
        }
        if (uniqueIds.size() > MAX_SUMMARY_BATCH_SIZE) {
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM,
                    "Số lượng variantIds truy vấn mỗi lần không vượt quá " + MAX_SUMMARY_BATCH_SIZE);
        }

        Map<UUID, Inventory> invByVariant = inventoryRepository
                .findByVariantIdInAndWarehouseCode(uniqueIds, MAIN_WAREHOUSE).stream()
                .collect(Collectors.toMap(Inventory::getVariantId, Function.identity(), (a, b) -> a));

        List<VariantPurchaseOrderLineView> openLines = poTrackingQueryService.findLinesByVariantIds(uniqueIds, false);
        Map<UUID, List<VariantPurchaseOrderLineView>> linesByVariant = openLines.stream()
                .collect(Collectors.groupingBy(VariantPurchaseOrderLineView::variantId));

        LocalDate today = LocalDate.now();
        Map<UUID, VariantInboundSummaryResponse> result = new LinkedHashMap<>();

        for (UUID variantId : uniqueIds) {
            List<VariantPurchaseOrderLineView> lines = linesByVariant.getOrDefault(variantId, Collections.emptyList());
            BigDecimal incomingQty = BigDecimal.ZERO;
            Set<UUID> poIds = new HashSet<>();
            LocalDate nearestExpected = null;
            boolean hasOverdue = false;

            for (VariantPurchaseOrderLineView line : lines) {
                BigDecimal rem = safeQty(line.remainingQty());
                if (rem.compareTo(BigDecimal.ZERO) <= 0) {
                    continue;
                }
                incomingQty = incomingQty.add(rem);
                poIds.add(line.purchaseOrderId());
                LocalDate exp = line.expectedDate();
                if (exp != null) {
                    if (exp.isBefore(today)) {
                        hasOverdue = true;
                    }
                    if (nearestExpected == null || exp.isBefore(nearestExpected)) {
                        nearestExpected = exp;
                    }
                }
            }

            Inventory inv = invByVariant.get(variantId);
            String hint = computeRestockHint(inv, incomingQty);
            BigDecimal suggestedQty = computeSuggestedOrderQty(inv, incomingQty, hint);

            result.put(variantId, VariantInboundSummaryResponse.builder()
                    .variantId(variantId)
                    .incomingQty(incomingQty)
                    .openPoCount(poIds.size())
                    .nearestExpectedDate(nearestExpected)
                    .hasOverduePo(hasOverdue)
                    .suggestedOrderQty(suggestedQty)
                    .restockActionHint(hint)
                    .build());
        }

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public VariantInboundDetailResponse getVariantPurchaseOrders(UUID variantId, boolean includeCompleted) {
        Inventory inv = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        List<VariantPurchaseOrderLineView> lines = poTrackingQueryService
                .findLinesByVariantIds(List.of(variantId), includeCompleted);

        Set<UUID> poIds = lines.stream().map(VariantPurchaseOrderLineView::purchaseOrderId).collect(Collectors.toSet());
        List<GoodsReceipt> receipts = poIds.isEmpty()
                ? Collections.emptyList()
                : goodsReceiptRepository.findByPurchaseOrderIdInOrderByCreatedAtDesc(poIds);

        Set<UUID> receiptIds = receipts.stream().map(GoodsReceipt::getGoodsReceiptId).collect(Collectors.toSet());
        Map<UUID, BigDecimal> variantQtyByReceiptId = receiptIds.isEmpty()
                ? Collections.emptyMap()
                : goodsReceiptItemRepository.findByGoodsReceiptIdInAndVariantId(receiptIds, variantId).stream()
                        .collect(Collectors.toMap(
                                GoodsReceiptItem::getGoodsReceiptId,
                                item -> safeQty(item.getQuantity()),
                                BigDecimal::add));

        Set<UUID> actorIds = receipts.stream()
                .map(gr -> gr.getConfirmedBy() != null ? gr.getConfirmedBy() : gr.getCreatedBy())
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> userNames = resolveUserNames(actorIds);

        Map<UUID, List<GoodsReceipt>> receiptsByPoId = receipts.stream()
                .collect(Collectors.groupingBy(GoodsReceipt::getPurchaseOrderId));

        LocalDate today = LocalDate.now();
        BigDecimal totalIncoming = BigDecimal.ZERO;
        int openPoCount = 0;
        boolean hasOverdue = false;
        List<PurchaseOrderTrackingItem> poItems = new ArrayList<>(lines.size());

        for (VariantPurchaseOrderLineView line : lines) {
            BigDecimal ordered = safeQty(line.orderedQty());
            BigDecimal received = safeQty(line.receivedQty());
            BigDecimal remaining = safeQty(line.remainingQty());
            boolean isOpen = "CONFIRMED".equals(line.status()) || "PARTIALLY_RECEIVED".equals(line.status());
            boolean overdue = isOpen && remaining.compareTo(BigDecimal.ZERO) > 0
                    && line.expectedDate() != null && line.expectedDate().isBefore(today);

            if (isOpen && remaining.compareTo(BigDecimal.ZERO) > 0) {
                totalIncoming = totalIncoming.add(remaining);
                openPoCount++;
                if (overdue) {
                    hasOverdue = true;
                }
            }

            int progressPercent = ordered.compareTo(BigDecimal.ZERO) > 0
                    ? received.multiply(BigDecimal.valueOf(100))
                            .divide(ordered, 0, RoundingMode.HALF_UP)
                            .min(BigDecimal.valueOf(100))
                            .intValue()
                    : 0;

            List<VariantGoodsReceiptBrief> variantReceipts = receiptsByPoId
                    .getOrDefault(line.purchaseOrderId(), Collections.emptyList()).stream()
                    .filter(gr -> variantQtyByReceiptId.containsKey(gr.getGoodsReceiptId()))
                    .map(gr -> {
                        UUID actor = gr.getConfirmedBy() != null ? gr.getConfirmedBy() : gr.getCreatedBy();
                        return VariantGoodsReceiptBrief.builder()
                                .goodsReceiptId(gr.getGoodsReceiptId())
                                .receiptCode(gr.getReceiptCode())
                                .receiptDate(gr.getReceiptDate())
                                .confirmedAt(gr.getConfirmedAt())
                                .receivedQty(variantQtyByReceiptId.get(gr.getGoodsReceiptId()))
                                .confirmedByName(userNames.get(actor))
                                .build();
                    })
                    .toList();

            poItems.add(PurchaseOrderTrackingItem.builder()
                    .purchaseOrderId(line.purchaseOrderId())
                    .purchaseOrderItemId(line.purchaseOrderItemId())
                    .poCode(line.poCode())
                    .status(line.status())
                    .statusLabel(line.statusLabel())
                    .supplierId(line.supplierId())
                    .supplierCode(line.supplierCode())
                    .supplierName(line.supplierName())
                    .orderDate(line.orderDate())
                    .expectedDate(line.expectedDate())
                    .overdue(overdue)
                    .unit(line.unit())
                    .orderedQty(ordered)
                    .receivedQty(received)
                    .remainingQty(remaining)
                    .unitCost(line.unitCost())
                    .progressPercent(progressPercent)
                    .receipts(variantReceipts)
                    .build());
        }

        BigDecimal available = inv.getAvailableQty();
        return VariantInboundDetailResponse.builder()
                .variantId(variantId)
                .onHandQty(safeQty(inv.getOnHandQty()))
                .reservedQty(safeQty(inv.getReservedQty()))
                .availableQty(available)
                .minimumQty(safeQty(inv.getMinimumQty()))
                .maximumQty(inv.getMaximumQty())
                .incomingQty(totalIncoming)
                .projectedAvailableQty(available.add(totalIncoming))
                .openPoCount(openPoCount)
                .hasOverduePo(hasOverdue)
                .purchaseOrders(poItems)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ReadableStockMovementResponse> getReadableMovements(UUID variantId, String group, Pageable pageable) {
        if (inventoryRepository.findByVariantId(variantId).isEmpty()) {
            throw new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId);
        }

        Pageable safePageable = PageableUtils.sanitizePageable(
                pageable, Set.of("createdAt", "movementType", "quantity", "balanceAfter"));

        List<MovementType> filterTypes = resolveMovementGroup(group);
        Page<StockMovement> page = filterTypes == null
                ? stockMovementRepository.findByVariantIdOrderByCreatedAtDesc(variantId, safePageable)
                : stockMovementRepository.findByVariantIdAndMovementTypeInOrderByCreatedAtDesc(
                        variantId, filterTypes, safePageable);

        if (page.isEmpty()) {
            return Page.empty(safePageable);
        }

        List<StockMovement> movements = page.getContent();

        // 1. Batch tra cứu GoodsReceipt + PurchaseOrder cho các dòng GOODS_RECEIPT
        Set<String> receiptCodes = movements.stream()
                .filter(m -> m.getMovementType() == MovementType.GOODS_RECEIPT && StringUtils.hasText(m.getReferenceId()))
                .map(StockMovement::getReferenceId)
                .collect(Collectors.toSet());

        Map<String, GoodsReceipt> grByCode = receiptCodes.isEmpty()
                ? Collections.emptyMap()
                : goodsReceiptRepository.findByReceiptCodeIn(receiptCodes).stream()
                        .collect(Collectors.toMap(GoodsReceipt::getReceiptCode, Function.identity(), (a, b) -> a));

        Set<UUID> poIds = grByCode.values().stream()
                .map(GoodsReceipt::getPurchaseOrderId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, PurchaseOrderBriefView> poBriefById = poIds.isEmpty()
                ? Collections.emptyMap()
                : poTrackingQueryService.findOrderBriefsByIds(poIds);

        // 2. Giải mã an toàn thông tin đơn bán / phiếu xuất cho các dòng STOCK_DISPATCH
        Map<UUID, ParsedDispatchPayload> dispatchByMovementId = new HashMap<>();
        Set<UUID> orderIds = new HashSet<>();
        for (StockMovement m : movements) {
            if (m.getMovementType() == MovementType.STOCK_DISPATCH) {
                ParsedDispatchPayload parsed = parseDispatchPayload(m);
                dispatchByMovementId.put(m.getMovementId(), parsed);
                if (parsed.orderId() != null) {
                    orderIds.add(parsed.orderId());
                }
            }
        }
        Map<UUID, String> orderCodeById = orderIds.isEmpty()
                ? Collections.emptyMap()
                : safeGetOrderCodes(orderIds);

        // 3. Batch tra cứu tên người thực hiện từ IAM
        Set<UUID> actorIds = movements.stream()
                .map(StockMovement::getCreatedBy)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, String> userNames = resolveUserNames(actorIds);

        return page.map(m -> toReadableMovement(m, grByCode, poBriefById, dispatchByMovementId, orderCodeById, userNames));
    }

    // =========================================================================
    // Internal Helpers
    // =========================================================================

    private ReadableStockMovementResponse toReadableMovement(
            StockMovement m,
            Map<String, GoodsReceipt> grByCode,
            Map<UUID, PurchaseOrderBriefView> poBriefById,
            Map<UUID, ParsedDispatchPayload> dispatchByMovementId,
            Map<UUID, String> orderCodeById,
            Map<UUID, String> userNames) {

        String actorName = m.getCreatedBy() != null ? userNames.get(m.getCreatedBy()) : null;
        MovementType type = m.getMovementType();

        if (type == MovementType.GOODS_RECEIPT) {
            String receiptCode = m.getReferenceId();
            GoodsReceipt gr = receiptCode != null ? grByCode.get(receiptCode) : null;
            PurchaseOrderBriefView po = gr != null ? poBriefById.get(gr.getPurchaseOrderId()) : null;

            String displayNote = buildGoodsReceiptNote(receiptCode, gr, po, m.getNote());
            return ReadableStockMovementResponse.builder()
                    .movementId(m.getMovementId())
                    .createdAt(m.getCreatedAt())
                    .movementType(type)
                    .movementTypeLabel(toVietnameseMovementType(type))
                    .quantity(m.getQuantity())
                    .balanceAfter(m.getBalanceAfter())
                    .referenceType(m.getReferenceType())
                    .documentCode(StringUtils.hasText(receiptCode) ? receiptCode : "NHẬP KHO")
                    .goodsReceiptId(gr != null ? gr.getGoodsReceiptId() : null)
                    .purchaseOrderId(gr != null ? gr.getPurchaseOrderId() : null)
                    .poCode(po != null ? po.poCode() : null)
                    .poStatus(po != null ? po.status() : null)
                    .supplierName(po != null ? po.supplierName() : null)
                    .actorId(m.getCreatedBy())
                    .actorName(actorName)
                    .displayNote(displayNote)
                    .build();
        }

        if (type == MovementType.STOCK_DISPATCH) {
            ParsedDispatchPayload dispatch = dispatchByMovementId.getOrDefault(
                    m.getMovementId(), new ParsedDispatchPayload(null, null));
            String orderCode = dispatch.orderId() != null ? orderCodeById.get(dispatch.orderId()) : null;
            String shortDelivery = dispatch.deliveryId() != null
                    ? "Phiếu xuất #" + dispatch.deliveryId().toString().substring(0, 8)
                    : "Xuất giao hàng";

            String documentCode = StringUtils.hasText(orderCode) ? orderCode : shortDelivery;
            String displayNote = StringUtils.hasText(orderCode)
                    ? "Xuất kho giao hàng cho đơn " + orderCode
                    : "Xuất kho giao đơn hàng" + (dispatch.deliveryId() != null ? " (" + shortDelivery + ")" : "");

            return ReadableStockMovementResponse.builder()
                    .movementId(m.getMovementId())
                    .createdAt(m.getCreatedAt())
                    .movementType(type)
                    .movementTypeLabel(toVietnameseMovementType(type))
                    .quantity(m.getQuantity())
                    .balanceAfter(m.getBalanceAfter())
                    .referenceType(m.getReferenceType())
                    .documentCode(documentCode)
                    .deliveryId(dispatch.deliveryId())
                    .orderId(dispatch.orderId())
                    .orderCode(orderCode)
                    .actorId(m.getCreatedBy())
                    .actorName(actorName)
                    .displayNote(displayNote)
                    .build();
        }

        String cleanNote = sanitizeRawNote(m.getNote(), type);
        String docCode = formatGenericDocumentCode(m.getReferenceType(), m.getReferenceId());

        return ReadableStockMovementResponse.builder()
                .movementId(m.getMovementId())
                .createdAt(m.getCreatedAt())
                .movementType(type)
                .movementTypeLabel(toVietnameseMovementType(type))
                .quantity(m.getQuantity())
                .balanceAfter(m.getBalanceAfter())
                .referenceType(m.getReferenceType())
                .documentCode(docCode)
                .actorId(m.getCreatedBy())
                .actorName(actorName)
                .displayNote(cleanNote)
                .build();
    }

    private static String buildGoodsReceiptNote(
            String receiptCode, GoodsReceipt gr, PurchaseOrderBriefView po, String fallbackNote) {
        if (po != null) {
            StringBuilder sb = new StringBuilder("Nhập kho");
            if (StringUtils.hasText(po.supplierName())) {
                sb.append(" từ ").append(po.supplierName());
            }
            if (StringUtils.hasText(po.poCode())) {
                sb.append(" (Đơn ").append(po.poCode()).append(")");
            }
            if (gr != null && StringUtils.hasText(gr.getNote())) {
                sb.append(" — ").append(gr.getNote().trim());
            }
            return sb.toString();
        }
        if (StringUtils.hasText(receiptCode)) {
            return "Nhập kho theo phiếu " + receiptCode;
        }
        return sanitizeRawNote(fallbackNote, MovementType.GOODS_RECEIPT);
    }

    private ParsedDispatchPayload parseDispatchPayload(StockMovement m) {
        UUID deliveryId = tryParseUuid(m.getReferenceId());
        UUID orderId = null;
        String raw = m.getNote();
        if (StringUtils.hasText(raw) && raw.trim().startsWith("{")) {
            try {
                JsonNode node = objectMapper.readTree(raw);
                if (deliveryId == null && node.hasNonNull("deliveryId")) {
                    deliveryId = tryParseUuid(node.get("deliveryId").asText());
                }
                if (node.hasNonNull("orderId")) {
                    orderId = tryParseUuid(node.get("orderId").asText());
                }
            } catch (Exception ex) {
                log.debug("Could not parse dispatch JSON note for movement {}", m.getMovementId());
            }
        }
        return new ParsedDispatchPayload(deliveryId, orderId);
    }

    private static String sanitizeRawNote(String rawNote, MovementType type) {
        if (!StringUtils.hasText(rawNote)) {
            return toVietnameseMovementType(type);
        }
        String trimmed = rawNote.trim();
        if (trimmed.startsWith("{") && trimmed.endsWith("}")) {
            return toVietnameseMovementType(type);
        }
        return trimmed;
    }

    private static String formatGenericDocumentCode(String referenceType, String referenceId) {
        if (StringUtils.hasText(referenceId)) {
            UUID asUuid = tryParseUuid(referenceId);
            if (asUuid != null) {
                return "#" + referenceId.substring(0, 8);
            }
            return referenceId;
        }
        if ("MANUAL_ADJUSTMENT".equalsIgnoreCase(referenceType)) {
            return "ĐIỀU CHỈNH";
        }
        if ("STOCK_COUNT".equalsIgnoreCase(referenceType)) {
            return "KIỂM KÊ";
        }
        if ("EXCEL_IMPORT".equalsIgnoreCase(referenceType)) {
            return "NHẬP EXCEL";
        }
        return "—";
    }

    public static String toVietnameseMovementType(MovementType type) {
        if (type == null) return "Khác";
        return switch (type) {
            case INITIAL_IMPORT -> "Khởi tạo đầu kỳ";
            case GOODS_RECEIPT -> "Nhập mua hàng";
            case STOCK_DISPATCH -> "Xuất kho";
            case RETURN_IN -> "Khách trả hàng";
            case SUPPLIER_RETURN -> "Trả hàng NCC";
            case ADJUSTMENT_IN -> "Điều chỉnh tăng";
            case ADJUSTMENT_OUT -> "Điều chỉnh giảm";
        };
    }

    private static List<MovementType> resolveMovementGroup(String group) {
        if (!StringUtils.hasText(group) || "ALL".equalsIgnoreCase(group.trim())) {
            return null;
        }
        return switch (group.trim().toUpperCase(Locale.ROOT)) {
            case "INBOUND" -> List.of(MovementType.GOODS_RECEIPT, MovementType.INITIAL_IMPORT, MovementType.RETURN_IN);
            case "OUTBOUND" -> List.of(MovementType.STOCK_DISPATCH, MovementType.SUPPLIER_RETURN);
            case "ADJUSTMENT" -> List.of(MovementType.ADJUSTMENT_IN, MovementType.ADJUSTMENT_OUT);
            default -> throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM,
                    "Nhóm biến động không hợp lệ (hỗ trợ ALL, INBOUND, OUTBOUND, ADJUSTMENT): " + group);
        };
    }

    private static String computeRestockHint(Inventory inv, BigDecimal incomingQty) {
        if (inv == null) {
            return incomingQty.compareTo(BigDecimal.ZERO) > 0 ? "INCOMING_COVERED" : "NONE";
        }
        BigDecimal available = inv.getAvailableQty();
        BigDecimal min = safeQty(inv.getMinimumQty());
        boolean outOfStock = available.compareTo(BigDecimal.ZERO) <= 0;
        boolean lowStock = inv.isLowStock();

        if (!outOfStock && !lowStock) {
            return "NONE";
        }
        if (incomingQty.compareTo(BigDecimal.ZERO) <= 0) {
            return "NEED_ORDER";
        }
        BigDecimal projected = available.add(incomingQty);
        boolean stillOutOrLow = projected.compareTo(BigDecimal.ZERO) <= 0
                || (min.compareTo(BigDecimal.ZERO) > 0 && projected.compareTo(min) <= 0);
        return stillOutOrLow ? "INCOMING_PARTIAL" : "INCOMING_COVERED";
    }

    private static BigDecimal computeSuggestedOrderQty(Inventory inv, BigDecimal incomingQty, String hint) {
        if (inv == null || "NONE".equals(hint) || "INCOMING_COVERED".equals(hint)) {
            return BigDecimal.ZERO;
        }
        BigDecimal available = inv.getAvailableQty();
        BigDecimal min = safeQty(inv.getMinimumQty());
        BigDecimal max = inv.getMaximumQty();
        BigDecimal target = (max != null && max.compareTo(BigDecimal.ZERO) > 0) ? max : min;
        BigDecimal deficit = target.subtract(available).subtract(incomingQty);
        if (deficit.compareTo(BigDecimal.ZERO) > 0) {
            return deficit;
        }
        return available.add(incomingQty).compareTo(BigDecimal.ZERO) <= 0 ? BigDecimal.ONE : BigDecimal.ZERO;
    }

    private Map<UUID, String> resolveUserNames(Set<UUID> userIds) {
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<UUID, String> map = new HashMap<>();
        for (UUID uid : userIds) {
            try {
                UserSummaryDto user = iamApi.getUserById(uid);
                if (user != null && StringUtils.hasText(user.fullName())) {
                    map.put(uid, user.fullName());
                }
            } catch (Exception ex) {
                log.debug("User {} not found in IAM", uid);
            }
        }
        return map;
    }

    private Map<UUID, String> safeGetOrderCodes(Set<UUID> orderIds) {
        try {
            Map<UUID, String> res = salesQueryService.getOrderCodesByIds(orderIds);
            return res != null ? res : Collections.emptyMap();
        } catch (Exception ex) {
            log.debug("Failed to resolve order codes for movements", ex);
            return Collections.emptyMap();
        }
    }

    private static UUID tryParseUuid(String raw) {
        if (!StringUtils.hasText(raw)) return null;
        try {
            return UUID.fromString(raw.trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static BigDecimal safeQty(BigDecimal qty) {
        return qty != null ? qty : BigDecimal.ZERO;
    }

    private record ParsedDispatchPayload(UUID deliveryId, UUID orderId) {}
}
