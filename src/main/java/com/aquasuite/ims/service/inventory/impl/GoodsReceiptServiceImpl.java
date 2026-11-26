package com.aquasuite.ims.service.inventory.impl;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.enums.inventory.MovementType;
import com.aquasuite.ims.entity.inventory.GoodsReceipt;
import com.aquasuite.ims.entity.inventory.GoodsReceiptItem;
import com.aquasuite.ims.entity.inventory.Inventory;
import com.aquasuite.ims.entity.inventory.StockMovement;
import com.aquasuite.ims.exception.inventory.InventoryErrorCode;
import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptItemRequest;
import com.aquasuite.ims.dto.inventory.request.CreateGoodsReceiptRequest;
import com.aquasuite.ims.dto.inventory.response.GoodsReceiptItemResponse;
import com.aquasuite.ims.dto.inventory.response.GoodsReceiptResponse;
import com.aquasuite.ims.repository.inventory.GoodsReceiptItemRepository;
import com.aquasuite.ims.repository.inventory.GoodsReceiptRepository;
import com.aquasuite.ims.repository.inventory.InventoryRepository;
import com.aquasuite.ims.repository.inventory.StockMovementRepository;
import com.aquasuite.ims.service.inventory.GoodsReceiptService;
import com.aquasuite.ims.service.purchasing.PurchaseOrderService;
import com.aquasuite.ims.dto.purchasing.response.ReceivableItemView;
import com.aquasuite.ims.dto.purchasing.response.ReceivablePurchaseOrderView;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Triển khai nghiệp vụ nhập kho theo đơn mua hàng (UC-32) theo Hướng B đã chốt.
 *
 * <p>Ranh giới module: service này chỉ khóa/đọc đơn mua hàng và cập nhật tiến độ nhận qua public
 * contract {@link PurchaseOrderService} + DTO {@code dto.purchasing}, không truy cập entity/repository của Purchasing. Toàn bộ thao tác
 * (ghi phiếu, cộng tồn, ghi thẻ kho, cập nhật tiến độ PO) chạy trong một transaction duy nhất.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GoodsReceiptServiceImpl implements GoodsReceiptService {

    private final GoodsReceiptRepository goodsReceiptRepository;
    private final GoodsReceiptItemRepository goodsReceiptItemRepository;
    private final InventoryRepository inventoryRepository;
    private final StockMovementRepository stockMovementRepository;
    private final PurchaseOrderService purchasingApi;
    private final IamQueryService iamApi;

    private static final String RECEIPT_STATUS_CONFIRMED = "CONFIRMED";
    private static final String REFERENCE_TYPE_GOODS_RECEIPT = "GOODS_RECEIPT";
    private static final String IDEMPOTENCY_LOCK_PREFIX = "goods-receipt-key:";

    /**
     * Lập và xác nhận phiếu nhập kho trong một hành động atomic (Hướng B).
     *
     * <p>Thứ tự khóa cố định (tránh deadlock): khóa advisory theo idempotency key → dòng PO
     * (FOR UPDATE, qua public contract Purchasing) → các dòng tồn theo thứ tự variantId.</p>
     *
     * <p>Trình tự: (0) khóa key, nếu key đã có phiếu thì trả lại phiếu cũ (cùng payload) hoặc từ chối
     * {@code INV_019} (khác payload); (1) khóa PO và đọc số còn lại mới nhất; (2) duyệt dòng theo
     * {@link #validateLines}; (3) ghi phiếu CONFIRMED; (4) cộng tồn + ghi thẻ kho; (5) cập nhật tiến độ
     * PO qua {@link PurchaseOrderService#applyGoodsReceipt}. Mọi lỗi ở bất kỳ bước nào cuộn ngược toàn bộ.</p>
     */
    @Override
    @Transactional
    public GoodsReceiptResponse createAndConfirm(CreateGoodsReceiptRequest request, UUID actorUserId) {
        UUID actorId = actorUserId != null ? actorUserId : iamApi.getCurrentUserId();
        UUID purchaseOrderId = request.purchaseOrderId();
        UUID idempotencyKey = request.idempotencyKey();
        if (idempotencyKey == null) {
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        }
        String requestHash = requestHash(request);

        // 0. Chống ghi trùng: tuần tự hóa theo key rồi kiểm tra phiếu đã ghi với key này.
        goodsReceiptRepository.lockIdempotencyKey(IDEMPOTENCY_LOCK_PREFIX + idempotencyKey);
        Optional<GoodsReceipt> prior = goodsReceiptRepository.findByIdempotencyKey(idempotencyKey);
        if (prior.isPresent()) {
            GoodsReceipt existing = prior.get();
            if (!requestHash.equals(existing.getRequestHash())) {
                throw new AppException(InventoryErrorCode.GOODS_RECEIPT_IDEMPOTENCY_CONFLICT, idempotencyKey);
            }
            log.info("Replay goods receipt {} for idempotency key {}", existing.getReceiptCode(), idempotencyKey);
            return mapToResponse(existing, mapItems(existing.getGoodsReceiptId()));
        }

        // 1. Khóa PO và đọc snapshot mới nhất qua public contract của Purchasing. Khóa giữ đến commit,
        //    nên số còn lại dưới đây không thể bị phiếu khác, hủy hoặc đóng PO thay đổi chen giữa.
        ReceivablePurchaseOrderView po = purchasingApi.lockReceivablePurchaseOrder(purchaseOrderId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_FOUND, purchaseOrderId));

        if (!po.receivable()) {
            throw new AppException(InventoryErrorCode.GOODS_RECEIPT_PO_NOT_RECEIVABLE, po.status());
        }

        // 2. Duyệt hợp lệ các dòng; trả về map dòng PO để tra cứu đơn giá/đơn vị/variant.
        Map<UUID, ReceivableItemView> poItemById = indexByPurchaseOrderItemId(po);
        List<CreateGoodsReceiptItemRequest> lines = validateLines(request.items(), poItemById);

        // 3. Ghi phiếu nhập ở trạng thái CONFIRMED (Hướng B: tạo = xác nhận).
        OffsetDateTime now = OffsetDateTime.now();
        GoodsReceipt receipt = GoodsReceipt.builder()
                .idempotencyKey(idempotencyKey)
                .requestHash(requestHash)
                .receiptCode(generateReceiptCode())
                .purchaseOrderId(purchaseOrderId)
                .status(RECEIPT_STATUS_CONFIRMED)
                .receiptDate(LocalDate.now())
                .supplierInvoiceNo(request.supplierInvoiceNo())
                .invoiceImageUrl(request.invoiceImageUrl())
                .note(request.note())
                .createdBy(actorId)
                .confirmedBy(actorId)
                .confirmedAt(now)
                .totalAmount(BigDecimal.ZERO)
                .build();
        receipt = goodsReceiptRepository.save(receipt);

        // 4. Với mỗi dòng: ghi chi tiết phiếu, cộng tồn theo (variant, kho) và ghi thẻ kho.
        //    Khóa dòng tồn theo thứ tự variantId cố định để tránh deadlock khi nhiều phiếu chạy song song.
        List<GoodsReceiptItemResponse> itemResponses = new ArrayList<>(lines.size());
        BigDecimal receiptTotal = BigDecimal.ZERO;
        Map<UUID, BigDecimal> receivedByPoItem = new LinkedHashMap<>();

        for (CreateGoodsReceiptItemRequest line : lines) {
            ReceivableItemView poItem = poItemById.get(line.purchaseOrderItemId());
            BigDecimal qty = line.quantity();
            BigDecimal unitCost = poItem.unitCost() != null ? poItem.unitCost() : BigDecimal.ZERO;
            BigDecimal lineTotal = unitCost.multiply(qty);
            receiptTotal = receiptTotal.add(lineTotal);

            GoodsReceiptItem item = GoodsReceiptItem.builder()
                    .goodsReceiptId(receipt.getGoodsReceiptId())
                    .purchaseOrderItemId(poItem.purchaseOrderItemId())
                    .variantId(poItem.variantId())
                    .unit(poItem.unit())
                    .quantity(qty)
                    .unitCost(unitCost)
                    .lineTotal(lineTotal)
                    .build();
            item = goodsReceiptItemRepository.save(item);

            applyStockIncrease(poItem.variantId(), po.warehouseCode(), qty, receipt.getReceiptCode(), actorId);
            receivedByPoItem.put(poItem.purchaseOrderItemId(), qty);

            itemResponses.add(GoodsReceiptItemResponse.builder()
                    .goodsReceiptItemId(item.getGoodsReceiptItemId())
                    .purchaseOrderItemId(item.getPurchaseOrderItemId())
                    .variantId(poItem.variantId())
                    .unit(item.getUnit())
                    .quantity(item.getQuantity())
                    .unitCost(item.getUnitCost())
                    .lineTotal(item.getLineTotal())
                    .build());
        }

        receipt.setTotalAmount(receiptTotal);
        receipt = goodsReceiptRepository.save(receipt);

        // 5. Cập nhật tiến độ nhận + trạng thái PO dưới cùng khóa. Ném lỗi (không bỏ qua) nếu PO không
        //    còn nhận được hoặc vượt số còn lại, khiến phiếu/tồn/thẻ kho ở trên cuộn ngược cùng.
        String poStatus = purchasingApi.applyGoodsReceipt(purchaseOrderId, receivedByPoItem);

        log.info("Confirmed goods receipt {} for PO {} (PO now {}) by user {}",
                receipt.getReceiptCode(), purchaseOrderId, poStatus, actorId);
        return mapToResponse(receipt, itemResponses);
    }

    @Override
    @Transactional(readOnly = true)
    public GoodsReceiptResponse getDetail(UUID goodsReceiptId) {
        GoodsReceipt receipt = goodsReceiptRepository.findById(goodsReceiptId)
                .orElseThrow(() -> new AppException(InventoryErrorCode.GOODS_RECEIPT_NOT_FOUND, goodsReceiptId));

        return mapToResponse(receipt, mapItems(goodsReceiptId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<GoodsReceiptResponse> listByPurchaseOrder(UUID purchaseOrderId) {
        return goodsReceiptRepository.findByPurchaseOrderIdOrderByCreatedAtDesc(purchaseOrderId).stream()
                .map(gr -> mapToResponse(gr, null))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Page<GoodsReceiptResponse> list(Pageable pageable) {
        Pageable safePageable = com.aquasuite.ims.util.PageableUtils.sanitizePageable(
                pageable, java.util.Set.of("createdAt", "updatedAt", "receiptDate", "receiptCode", "status", "totalAmount"));
        return goodsReceiptRepository.findAllByOrderByCreatedAtDesc(safePageable)
                .map(gr -> mapToResponse(gr, null));
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /** Lập chỉ mục dòng PO theo purchaseOrderItemId để tra cứu nhanh khi duyệt dòng phiếu. */
    private Map<UUID, ReceivableItemView> indexByPurchaseOrderItemId(ReceivablePurchaseOrderView po) {
        Map<UUID, ReceivableItemView> map = new HashMap<>();
        for (ReceivableItemView item : po.items()) {
            map.put(item.purchaseOrderItemId(), item);
        }
        return map;
    }

    /**
     * Kiểm tra hợp lệ toàn bộ dòng nhập theo Business Rules UC-32 và trả về danh sách dòng có
     * số lượng dương, sắp xếp theo variantId để cố định thứ tự khóa tồn.
     *
     * <p>Quy tắc: không rỗng; không lặp dòng PO; dòng phải thuộc PO; số lượng > 0; không vượt
     * số còn phải nhận tại thời điểm hiện tại.</p>
     */
    private List<CreateGoodsReceiptItemRequest> validateLines(
            List<CreateGoodsReceiptItemRequest> items,
            Map<UUID, ReceivableItemView> poItemById) {

        if (items == null || items.isEmpty()) {
            throw new AppException(InventoryErrorCode.GOODS_RECEIPT_EMPTY_ITEMS);
        }

        Set<UUID> seen = new HashSet<>();
        List<CreateGoodsReceiptItemRequest> effective = new ArrayList<>(items.size());

        for (CreateGoodsReceiptItemRequest line : items) {
            UUID poItemId = line.purchaseOrderItemId();
            if (!seen.add(poItemId)) {
                throw new AppException(InventoryErrorCode.GOODS_RECEIPT_DUPLICATE_ITEM, poItemId);
            }

            ReceivableItemView poItem = poItemById.get(poItemId);
            if (poItem == null) {
                throw new AppException(InventoryErrorCode.GOODS_RECEIPT_ITEM_NOT_IN_PO, poItemId);
            }

            BigDecimal qty = line.quantity();
            if (qty == null || qty.compareTo(BigDecimal.ZERO) <= 0) {
                throw new AppException(InventoryErrorCode.GOODS_RECEIPT_INVALID_QUANTITY, qty);
            }

            BigDecimal remaining = poItem.remainingQty() != null ? poItem.remainingQty() : BigDecimal.ZERO;
            if (qty.compareTo(remaining) > 0) {
                throw new AppException(InventoryErrorCode.GOODS_RECEIPT_EXCEEDS_REMAINING, qty, remaining);
            }

            effective.add(line);
        }

        // Tất cả dòng request đều có số dương (đã validate), nên "ít nhất một dòng" luôn thỏa.
        effective.sort(Comparator.comparing(l -> poItemById.get(l.purchaseOrderItemId()).variantId()));
        return effective;
    }

    /**
     * Cộng tồn thực tế cho (variant, kho) dưới khóa bi quan và ghi một thẻ kho bất biến
     * kiểu {@link MovementType#GOODS_RECEIPT}. Nếu chưa có dòng tồn thì tạo mới an toàn
     * (unique index (variant_id, warehouse_code) chống tạo trùng).
     */
    private void applyStockIncrease(UUID variantId, String warehouseCode, BigDecimal qty,
                                    String receiptCode, UUID actorId) {
        // Tạo dòng tồn rỗng nếu chưa có (ON CONFLICT DO NOTHING) rồi khóa: hai phiếu khác PO cùng
        // nhập lần đầu một SKU không tạo trùng và không làm mất cập nhật của nhau.
        inventoryRepository.insertEmptyIfAbsent(UUID.randomUUID(), variantId, warehouseCode);
        Inventory inv = inventoryRepository.findByVariantIdAndWarehouseCodeForUpdate(variantId, warehouseCode)
                .orElseThrow(() -> new AppException(InventoryErrorCode.INVENTORY_NOT_FOUND, variantId));

        BigDecimal newOnHand = (inv.getOnHandQty() != null ? inv.getOnHandQty() : BigDecimal.ZERO).add(qty);
        inv.setOnHandQty(newOnHand);
        inv = inventoryRepository.save(inv);

        StockMovement movement = StockMovement.builder()
                .movementId(UUID.randomUUID())
                .inventoryId(inv.getInventoryId())
                .variantId(variantId)
                .movementType(MovementType.GOODS_RECEIPT)
                .quantity(qty)
                .balanceAfter(newOnHand)
                .referenceType(REFERENCE_TYPE_GOODS_RECEIPT)
                .referenceId(receiptCode)
                .note("Nhập kho theo phiếu [" + receiptCode + "]")
                .createdBy(actorId)
                .build();
        stockMovementRepository.save(movement);
    }

    /** Lấy và ánh xạ các dòng chi tiết của một phiếu nhập kho. */
    private List<GoodsReceiptItemResponse> mapItems(UUID goodsReceiptId) {
        return goodsReceiptItemRepository.findByGoodsReceiptId(goodsReceiptId).stream()
                .map(item -> GoodsReceiptItemResponse.builder()
                        .goodsReceiptItemId(item.getGoodsReceiptItemId())
                        .purchaseOrderItemId(item.getPurchaseOrderItemId())
                        .variantId(item.getVariantId())
                        .unit(item.getUnit())
                        .quantity(item.getQuantity())
                        .unitCost(item.getUnitCost())
                        .lineTotal(item.getLineTotal())
                        .build())
                .toList();
    }

    /** Ánh xạ entity phiếu nhập sang response; {@code items} có thể null ở API danh sách. */
    private GoodsReceiptResponse mapToResponse(GoodsReceipt receipt, List<GoodsReceiptItemResponse> items) {
        return GoodsReceiptResponse.builder()
                .goodsReceiptId(receipt.getGoodsReceiptId())
                .receiptCode(receipt.getReceiptCode())
                .purchaseOrderId(receipt.getPurchaseOrderId())
                .status(receipt.getStatus())
                .receiptDate(receipt.getReceiptDate())
                .supplierInvoiceNo(receipt.getSupplierInvoiceNo())
                .invoiceImageUrl(receipt.getInvoiceImageUrl())
                .totalAmount(receipt.getTotalAmount())
                .note(receipt.getNote())
                .createdBy(receipt.getCreatedBy())
                .confirmedBy(receipt.getConfirmedBy())
                .confirmedAt(receipt.getConfirmedAt())
                .createdAt(receipt.getCreatedAt())
                .items(items)
                .build();
    }

    /**
     * Băm SHA-256 payload đã chuẩn hóa: thứ tự dòng không quan trọng, số lượng so theo giá trị
     * (7 == 7.00). Mỗi trường được ghi kèm độ dài để không nhập nhằng giữa các trường.
     */
    private static String requestHash(CreateGoodsReceiptRequest request) {
        StringBuilder sb = new StringBuilder("goods-receipt:v1");
        appendField(sb, request.purchaseOrderId() == null ? null : request.purchaseOrderId().toString());
        appendField(sb, request.supplierInvoiceNo());
        appendField(sb, request.invoiceImageUrl());
        appendField(sb, request.note());
        List<String> lines = new ArrayList<>();
        if (request.items() != null) {
            for (CreateGoodsReceiptItemRequest item : request.items()) {
                String id = item == null || item.purchaseOrderItemId() == null ? "-" : item.purchaseOrderItemId().toString();
                String qty = item == null || item.quantity() == null ? "-" : item.quantity().stripTrailingZeros().toPlainString();
                lines.add(id + "=" + qty);
            }
        }
        Collections.sort(lines);
        for (String line : lines) {
            appendField(sb, line);
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static void appendField(StringBuilder sb, String value) {
        if (value == null) {
            sb.append("|n");
        } else {
            sb.append('|').append(value.length()).append(':').append(value);
        }
    }

    /** Sinh mã phiếu nhập kho dạng GRN + dấu thời gian UTC + số ngẫu nhiên, tương tự các phiếu khác. */
    private String generateReceiptCode() {
        String timestamp = DateTimeFormatter.ofPattern("yyMMddHHmm").withZone(ZoneOffset.UTC)
                .format(java.time.Instant.now());
        int rand = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "GRN" + timestamp + rand;
    }
}
