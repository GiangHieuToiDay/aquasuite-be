package com.aquasuite.ims.module.sales.internal.service.helper;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceStatus;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceType;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.payload.request.InitialInvoiceBatchRequest;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.share.exception.AppException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Helper dùng chung để khởi tạo hóa đơn DRAFT (STORE cho đơn lẻ, PROJECT theo đợt cho đơn công trình).
 */
public final class SalesInvoiceDraftInitializer {

    private SalesInvoiceDraftInitializer() {
    }

    public static List<SalesInvoice> initializeForProjectOrder(
            Order order,
            List<OrderItem> orderItems,
            List<InitialInvoiceBatchRequest> batches,
            UUID actorId,
            SalesInvoiceRepository invoiceRepository,
            SalesInvoiceItemRepository invoiceItemRepository
    ) {
        if (order == null || orderItems == null || orderItems.isEmpty()) {
            return List.of();
        }
        OffsetDateTime now = order.getOrderDate() != null ? order.getOrderDate() : OffsetDateTime.now();
        BigDecimal vatRate = order.getVatRate() != null && order.getVatRate().compareTo(BigDecimal.ZERO) > 0
                ? order.getVatRate()
                : BigDecimal.ZERO;

        if (batches == null || batches.isEmpty()) {
            UUID invoiceId = UUID.randomUUID();
            BigDecimal subtotal = orderItems.stream()
                    .map(item -> item.getLineTotal() != null ? item.getLineTotal() : BigDecimal.ZERO)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal taxAmount = vatRate.compareTo(BigDecimal.ZERO) > 0
                    ? subtotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal total = subtotal.add(taxAmount);

            SalesInvoice invoiceToSave = SalesInvoice.builder()
                    .invoiceId(invoiceId)
                    .invoiceCode(buildInvoiceCode(order, 1))
                    .orderId(order.getOrderId())
                    .customerId(order.getCustomerId())
                    .invoiceType(SalesInvoiceType.PROJECT.name())
                    .batchNo(1)
                    .batchName("Đợt 1")
                    .issueDate(now)
                    .subtotal(subtotal)
                    .discountAmount(BigDecimal.ZERO)
                    .taxAmount(taxAmount)
                    .totalAmount(total)
                    .status(SalesInvoiceStatus.DRAFT.name())
                    .createdBy(actorId)
                    .createdAt(now)
                    .build();
            SalesInvoice savedInvoice = invoiceRepository.saveAndFlush(invoiceToSave);

            List<SalesInvoiceItem> invoiceItems = orderItems.stream()
                    .map(item -> buildInvoiceItem(invoiceId, item, item.getQuantity(), vatRate))
                    .toList();
            invoiceItemRepository.saveAll(invoiceItems);
            return List.of(savedInvoice != null ? savedInvoice : invoiceToSave);
        }

        Map<UUID, OrderItem> orderItemByVariant = validateBatchesAndIndex(orderItems, batches);

        List<SalesInvoice> createdInvoices = new ArrayList<>();
        int batchNo = 1;
        for (InitialInvoiceBatchRequest batch : batches) {
            UUID invoiceId = UUID.randomUUID();
            BigDecimal batchSubtotal = BigDecimal.ZERO;
            List<SalesInvoiceItem> batchInvoiceItems = new ArrayList<>();

            for (InitialInvoiceBatchRequest.BatchItemRequest bi : batch.getItems()) {
                OrderItem sourceItem = orderItemByVariant.get(bi.getVariantId());
                SalesInvoiceItem invItem = buildInvoiceItem(invoiceId, sourceItem, bi.getQuantity(), vatRate);
                batchInvoiceItems.add(invItem);
                batchSubtotal = batchSubtotal.add(invItem.getLineTotal());
            }

            BigDecimal taxAmount = vatRate.compareTo(BigDecimal.ZERO) > 0
                    ? batchSubtotal.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            BigDecimal batchTotal = batchSubtotal.add(taxAmount);

            SalesInvoice invoiceToSave = SalesInvoice.builder()
                    .invoiceId(invoiceId)
                    .invoiceCode(buildInvoiceCode(order, batchNo))
                    .orderId(order.getOrderId())
                    .customerId(order.getCustomerId())
                    .invoiceType(SalesInvoiceType.PROJECT.name())
                    .batchNo(batchNo)
                    .batchName(batch.getBatchName() != null && !batch.getBatchName().isBlank()
                            ? batch.getBatchName().trim()
                            : "Đợt " + batchNo)
                    .scheduledDeliveryDate(batch.getScheduledDeliveryDate())
                    .note(batch.getNote())
                    .issueDate(now)
                    .subtotal(batchSubtotal)
                    .discountAmount(BigDecimal.ZERO)
                    .taxAmount(taxAmount)
                    .totalAmount(batchTotal)
                    .status(SalesInvoiceStatus.DRAFT.name())
                    .createdBy(actorId)
                    .createdAt(now)
                    .build();
            SalesInvoice saved = invoiceRepository.saveAndFlush(invoiceToSave);
            invoiceItemRepository.saveAll(batchInvoiceItems);
            createdInvoices.add(saved != null ? saved : invoiceToSave);
            batchNo++;
        }
        return createdInvoices;
    }

    public static SalesInvoiceItem buildInvoiceItem(
            UUID invoiceId,
            OrderItem sourceOrderItem,
            BigDecimal quantity,
            BigDecimal taxRate
    ) {
        BigDecimal unitPrice = sourceOrderItem.getUnitPrice() != null ? sourceOrderItem.getUnitPrice() : BigDecimal.ZERO;
        BigDecimal itemDiscount = BigDecimal.ZERO;
        if (sourceOrderItem.getDiscountAmount() != null && sourceOrderItem.getDiscountAmount().compareTo(BigDecimal.ZERO) > 0
                && sourceOrderItem.getQuantity() != null && sourceOrderItem.getQuantity().compareTo(BigDecimal.ZERO) > 0) {
            if (quantity.compareTo(sourceOrderItem.getQuantity()) == 0) {
                itemDiscount = sourceOrderItem.getDiscountAmount();
            } else {
                itemDiscount = sourceOrderItem.getDiscountAmount()
                        .multiply(quantity)
                        .divide(sourceOrderItem.getQuantity(), 2, RoundingMode.HALF_UP);
            }
        }
        BigDecimal lineTotal = unitPrice.multiply(quantity).subtract(itemDiscount).setScale(2, RoundingMode.HALF_UP);
        if (lineTotal.compareTo(BigDecimal.ZERO) < 0) {
            lineTotal = BigDecimal.ZERO;
        }
        String description = sourceOrderItem.getProductName();
        if (sourceOrderItem.getVariantName() != null && !sourceOrderItem.getVariantName().isBlank()) {
            description = description + " - " + sourceOrderItem.getVariantName();
        }
        return SalesInvoiceItem.builder()
                .invoiceItemId(UUID.randomUUID())
                .invoiceId(invoiceId)
                .orderItemId(sourceOrderItem.getOrderItemId())
                .variantId(sourceOrderItem.getVariantId())
                .description(description)
                .quantity(quantity)
                .unitPrice(unitPrice)
                .discountAmount(itemDiscount)
                .taxRate(taxRate != null ? taxRate : BigDecimal.ZERO)
                .lineTotal(lineTotal)
                .build();
    }

    /** Kiểm tra tính hợp lệ của phân bổ sản phẩm theo đợt trước khi lưu đơn hàng. */
    public static void validateBatches(List<OrderItem> orderItems, List<InitialInvoiceBatchRequest> batches) {
        if (batches == null || batches.isEmpty()) {
            return;
        }
        validateBatchesAndIndex(orderItems, batches);
    }

    private static Map<UUID, OrderItem> validateBatchesAndIndex(
            List<OrderItem> orderItems,
            List<InitialInvoiceBatchRequest> batches
    ) {
        Map<UUID, BigDecimal> orderQtyByVariant = new LinkedHashMap<>();
        Map<UUID, OrderItem> orderItemByVariant = new LinkedHashMap<>();
        for (OrderItem item : orderItems) {
            orderQtyByVariant.merge(item.getVariantId(), item.getQuantity(), BigDecimal::add);
            orderItemByVariant.putIfAbsent(item.getVariantId(), item);
        }

        Map<UUID, BigDecimal> batchQtyByVariant = new LinkedHashMap<>();
        for (InitialInvoiceBatchRequest batch : batches) {
            if (batch.getItems() == null || batch.getItems().isEmpty()) {
                throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID, "Mỗi đợt phải có ít nhất 1 sản phẩm");
            }
            for (InitialInvoiceBatchRequest.BatchItemRequest bi : batch.getItems()) {
                if (bi.getQuantity() == null || bi.getQuantity().compareTo(BigDecimal.ZERO) <= 0) {
                    throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID, "Số lượng mỗi dòng phải lớn hơn 0");
                }
                if (!orderItemByVariant.containsKey(bi.getVariantId())) {
                    throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID,
                            "Sản phẩm " + bi.getVariantId() + " không thuộc đơn hàng");
                }
                batchQtyByVariant.merge(bi.getVariantId(), bi.getQuantity(), BigDecimal::add);
            }
        }

        for (Map.Entry<UUID, BigDecimal> entry : orderQtyByVariant.entrySet()) {
            BigDecimal allocated = batchQtyByVariant.getOrDefault(entry.getKey(), BigDecimal.ZERO);
            if (allocated.compareTo(entry.getValue()) != 0) {
                throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID,
                        "Tổng số lượng phân bổ theo các đợt cho SKU " + entry.getKey()
                                + " (" + allocated + ") phải bằng số lượng trên đơn (" + entry.getValue() + ")");
            }
        }
        return orderItemByVariant;
    }

    public static String buildInvoiceCode(Order order, int batchNo) {
        String base = order.getOrderCode() != null && !order.getOrderCode().isBlank()
                ? order.getOrderCode()
                : order.getOrderId().toString().substring(0, 8);
        String suffix = UUID.randomUUID().toString().substring(0, 4).toUpperCase();
        String code = String.format("INV-%s-B%02d-%s", base, batchNo, suffix);
        return code.length() <= 100 ? code : code.substring(0, 100);
    }
}
