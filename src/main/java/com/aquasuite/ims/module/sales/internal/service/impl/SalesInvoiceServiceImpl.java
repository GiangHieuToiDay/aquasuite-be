package com.aquasuite.ims.module.sales.internal.service.impl;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.finance.api.CreditControlApi;
import com.aquasuite.ims.module.finance.api.OrderReceivableApi;
import com.aquasuite.ims.module.finance.api.dto.OrderReceivableView;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceSummaryDto;
import com.aquasuite.ims.module.sales.api.event.SalesInvoiceIssuedEvent;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoice;
import com.aquasuite.ims.module.sales.internal.entity.SalesInvoiceItem;
import com.aquasuite.ims.module.sales.internal.enums.OrderModificationRequestStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderStatus;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentStatus;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceStatus;
import com.aquasuite.ims.module.sales.internal.enums.SalesInvoiceType;
import com.aquasuite.ims.module.sales.internal.error.SalesErrorCode;
import com.aquasuite.ims.module.sales.internal.payload.request.CreateDraftInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateSalesInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesInvoiceResponse;
import com.aquasuite.ims.module.sales.internal.repository.OrderItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderModificationRequestRepository;
import com.aquasuite.ims.module.sales.internal.repository.OrderRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceItemRepository;
import com.aquasuite.ims.module.sales.internal.repository.SalesInvoiceRepository;
import com.aquasuite.ims.module.sales.internal.service.SalesInvoiceService;
import com.aquasuite.ims.module.sales.internal.service.helper.SalesInvoiceDraftInitializer;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Triển khai vòng đời Hóa đơn bán hàng (DRAFT -> ISSUED / CANCELLED) cho cả đơn lẻ (STORE)
 * và đơn công trình theo đợt (PROJECT), tự động đồng bộ OrderItem, tồn kho giữ chỗ và tổng đơn.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SalesInvoiceServiceImpl implements SalesInvoiceService {

    private static final List<OrderModificationRequestStatus> ACTIVE_UC26_STATUSES = List.of(
            OrderModificationRequestStatus.PENDING_REVIEW,
            OrderModificationRequestStatus.PENDING_CANCEL
    );

    private final SalesInvoiceRepository salesInvoiceRepository;
    private final SalesInvoiceItemRepository salesInvoiceItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final OrderModificationRequestRepository orderModificationRequestRepository;
    private final CatalogApi catalogApi;
    private final InventoryApi inventoryApi;
    private final OrderReceivableApi orderReceivableApi;
    private final CreditControlApi creditControlApi;
    private final CrmApi crmApi;
    private final IamApi iamApi;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(readOnly = true)
    public List<SalesInvoiceResponse> getInvoicesByOrderId(UUID orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        List<SalesInvoice> invoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(orderId);
        if (invoices.isEmpty()) {
            return List.of();
        }

        List<UUID> invoiceIds = invoices.stream().map(SalesInvoice::getInvoiceId).toList();
        Map<UUID, List<SalesInvoiceItem>> itemsByInvoiceId = salesInvoiceItemRepository.findByInvoiceIdIn(invoiceIds)
                .stream()
                .collect(Collectors.groupingBy(SalesInvoiceItem::getInvoiceId));
        Map<UUID, OrderItem> orderItemMap = orderItemRepository.findByOrderId(orderId).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, oi -> oi, (a, b) -> a));
        boolean lockedByUc26 = hasActiveUc26(orderId);

        return invoices.stream()
                .map(inv -> toResponse(inv, order,
                        itemsByInvoiceId.getOrDefault(inv.getInvoiceId(), List.of()),
                        orderItemMap, lockedByUc26))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public SalesInvoiceResponse getInvoiceDetail(UUID invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new AppException(SalesErrorCode.INVOICE_NOT_FOUND, invoiceId));
        Order order = orderRepository.findById(invoice.getOrderId())
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));
        List<SalesInvoiceItem> items = salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(invoiceId);
        Map<UUID, OrderItem> orderItemMap = orderItemRepository.findByOrderId(order.getOrderId()).stream()
                .collect(Collectors.toMap(OrderItem::getOrderItemId, oi -> oi, (a, b) -> a));
        return toResponse(invoice, order, items, orderItemMap, hasActiveUc26(order.getOrderId()));
    }

    @Override
    @Transactional
    public SalesInvoiceResponse createDraftInvoice(UUID orderId, CreateDraftInvoiceRequest request) {
        Order order = orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        ensureOrderAllowsInvoiceMutation(order);
        ensureNotBlockedByUc26(orderId);

        if (OrderType.RETAIL.name().equalsIgnoreCase(order.getOrderType())
                && salesInvoiceRepository.findByOrderIdAndInvoiceType(orderId, SalesInvoiceType.STORE.name()).isPresent()) {
            throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID,
                    "Đơn bán lẻ tại quầy chỉ sử dụng 1 hóa đơn duy nhất, vui lòng chỉnh sửa trực tiếp trên hóa đơn hiện tại");
        }

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(SalesErrorCode.INVOICE_ITEMS_EMPTY);
        }

        Map<UUID, MergedLine> mergedLines = mergeCreateLines(request.getItems());
        List<SalesInvoice> existingInvoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(orderId);
        int nextBatchNo = existingInvoices.stream()
                .map(SalesInvoice::getBatchNo)
                .filter(b -> b != null)
                .max(Integer::compareTo)
                .orElse(0) + 1;

        BigDecimal vatRate = resolveOrderVatRate(order);
        OffsetDateTime now = OffsetDateTime.now();
        UUID actorId = resolveCurrentActorId(order.getSalesStaffId());
        UUID newInvoiceId = UUID.randomUUID();

        if (request.getSplitFromInvoiceId() != null) {
            return createBatchBySplittingDraft(
                    order, request, mergedLines, nextBatchNo, vatRate, now, actorId, newInvoiceId);
        }

        // Thêm đợt hàng mới bổ sung trực tiếp vào đơn hàng
        List<OrderItem> existingOrderItems = orderItemRepository.findByOrderId(orderId);
        Map<UUID, OrderItem> orderItemByVariant = existingOrderItems.stream()
                .collect(Collectors.toMap(OrderItem::getVariantId, oi -> oi, (a, b) -> a, LinkedHashMap::new));

        List<SalesInvoiceItem> newInvoiceItems = new ArrayList<>();
        BigDecimal batchSubtotal = BigDecimal.ZERO;

        for (MergedLine line : mergedLines.values()) {
            CatalogSkuSummaryDto sku = requireActiveSku(line.variantId());
            if (!order.isReserveStockAfterDeposit()) {
                if (!inventoryApi.checkAvailability(line.variantId(), line.quantity())) {
                    throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK, sku.sku());
                }
                inventoryApi.reserveStock(line.variantId(), line.quantity());
            }

            OrderItem targetOrderItem = orderItemByVariant.get(line.variantId());
            BigDecimal unitPrice = targetOrderItem != null && targetOrderItem.getUnitPrice() != null
                    ? targetOrderItem.getUnitPrice()
                    : safeAmount(sku.sellingPrice());

            BigDecimal lineDiscount = line.discountAmount();
            BigDecimal lineTotal = unitPrice.multiply(line.quantity()).subtract(lineDiscount)
                    .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);

            if (targetOrderItem == null) {
                targetOrderItem = OrderItem.builder()
                        .orderItemId(UUID.randomUUID())
                        .orderId(orderId)
                        .variantId(line.variantId())
                        .sku(sku.sku())
                        .productName(sku.productName() != null ? sku.productName() : sku.variantName())
                        .variantName(sku.variantName())
                        .quantity(line.quantity())
                        .unitPrice(unitPrice)
                        .discountAmount(lineDiscount)
                        .lineTotal(lineTotal)
                        .build();
                targetOrderItem = orderItemRepository.saveAndFlush(targetOrderItem);
                orderItemByVariant.put(line.variantId(), targetOrderItem);
            } else {
                BigDecimal updatedQty = targetOrderItem.getQuantity().add(line.quantity());
                BigDecimal updatedDiscount = safeAmount(targetOrderItem.getDiscountAmount()).add(lineDiscount);
                BigDecimal updatedTotal = unitPrice.multiply(updatedQty).subtract(updatedDiscount)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
                targetOrderItem.setQuantity(updatedQty);
                targetOrderItem.setDiscountAmount(updatedDiscount);
                targetOrderItem.setLineTotal(updatedTotal);
                orderItemRepository.saveAndFlush(targetOrderItem);
            }

            SalesInvoiceItem invoiceItem = SalesInvoiceItem.builder()
                    .invoiceItemId(UUID.randomUUID())
                    .invoiceId(newInvoiceId)
                    .orderItemId(targetOrderItem.getOrderItemId())
                    .variantId(line.variantId())
                    .description(buildDescription(targetOrderItem.getProductName(), targetOrderItem.getVariantName()))
                    .quantity(line.quantity())
                    .unitPrice(unitPrice)
                    .discountAmount(lineDiscount)
                    .taxRate(vatRate)
                    .lineTotal(lineTotal)
                    .build();
            newInvoiceItems.add(invoiceItem);
            batchSubtotal = batchSubtotal.add(lineTotal);
        }

        BigDecimal taxAmount = calculateTax(batchSubtotal, vatRate);
        BigDecimal batchTotal = batchSubtotal.add(taxAmount);
        String batchName = request.getBatchName() != null && !request.getBatchName().isBlank()
                ? request.getBatchName().trim()
                : "Đợt " + nextBatchNo;

        SalesInvoice created = salesInvoiceRepository.saveAndFlush(SalesInvoice.builder()
                .invoiceId(newInvoiceId)
                .invoiceCode(SalesInvoiceDraftInitializer.buildInvoiceCode(order, nextBatchNo))
                .orderId(orderId)
                .customerId(order.getCustomerId())
                .invoiceType(SalesInvoiceType.PROJECT.name())
                .batchNo(nextBatchNo)
                .batchName(batchName)
                .scheduledDeliveryDate(request.getScheduledDeliveryDate())
                .note(trimToNull(request.getNote()))
                .issueDate(now)
                .subtotal(batchSubtotal)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(taxAmount)
                .totalAmount(batchTotal)
                .status(SalesInvoiceStatus.DRAFT.name())
                .createdBy(actorId)
                .createdAt(now)
                .build());
        salesInvoiceItemRepository.saveAllAndFlush(newInvoiceItems);

        recalculateOrderTotalsAndPaymentFloor(order);
        return getInvoiceDetail(created.getInvoiceId());
    }

    @Override
    @Transactional
    public SalesInvoiceResponse updateDraftInvoice(UUID invoiceId, UpdateSalesInvoiceRequest request) {
        SalesInvoice invoice = salesInvoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new AppException(SalesErrorCode.INVOICE_NOT_FOUND, invoiceId));
        Order order = orderRepository.findByIdForUpdate(invoice.getOrderId())
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        ensureInvoiceEditableStatus(invoice, order);
        ensureNotBlockedByUc26(order.getOrderId());

        if (request == null || request.getItems() == null || request.getItems().isEmpty()) {
            throw new AppException(SalesErrorCode.INVOICE_ITEMS_EMPTY);
        }

        Map<UUID, MergedLine> newLines = mergeUpdateLines(request.getItems());
        List<SalesInvoiceItem> oldInvoiceItems = salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(invoiceId);

        // Gom số lượng cũ trên hóa đơn này theo SKU
        Map<UUID, BigDecimal> oldQtyByVariant = new LinkedHashMap<>();
        Map<UUID, BigDecimal> oldUnitPriceByVariant = new LinkedHashMap<>();
        for (SalesInvoiceItem item : oldInvoiceItems) {
            oldQtyByVariant.merge(item.getVariantId(), safeAmount(item.getQuantity()), BigDecimal::add);
            if (item.getUnitPrice() != null) {
                oldUnitPriceByVariant.putIfAbsent(item.getVariantId(), item.getUnitPrice());
            }
        }

        // Tìm tất cả các hóa đơn còn hiệu lực khác của cùng đơn hàng
        List<SalesInvoice> allOrderInvoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(order.getOrderId());
        List<UUID> otherActiveInvoiceIds = allOrderInvoices.stream()
                .filter(inv -> !inv.getInvoiceId().equals(invoiceId))
                .filter(inv -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(inv.getStatus()))
                .map(SalesInvoice::getInvoiceId)
                .toList();

        List<SalesInvoiceItem> otherInvoiceItems = otherActiveInvoiceIds.isEmpty()
                ? List.of()
                : salesInvoiceItemRepository.findByInvoiceIdIn(otherActiveInvoiceIds);

        Map<UUID, BigDecimal> otherQtyByVariant = new LinkedHashMap<>();
        Map<UUID, BigDecimal> otherDiscountByVariant = new LinkedHashMap<>();
        for (SalesInvoiceItem item : otherInvoiceItems) {
            otherQtyByVariant.merge(item.getVariantId(), safeAmount(item.getQuantity()), BigDecimal::add);
            otherDiscountByVariant.merge(item.getVariantId(), safeAmount(item.getDiscountAmount()), BigDecimal::add);
        }

        // Cập nhật chênh lệch giữ chỗ tồn kho
        Set<UUID> allTouchedVariants = new java.util.LinkedHashSet<>();
        allTouchedVariants.addAll(oldQtyByVariant.keySet());
        allTouchedVariants.addAll(newLines.keySet());

        Map<UUID, CatalogSkuSummaryDto> skuCache = new HashMap<>();
        for (UUID variantId : allTouchedVariants) {
            BigDecimal oldQty = oldQtyByVariant.getOrDefault(variantId, BigDecimal.ZERO);
            BigDecimal newQty = newLines.containsKey(variantId) ? newLines.get(variantId).quantity() : BigDecimal.ZERO;
            BigDecimal delta = newQty.subtract(oldQty);

            if (delta.compareTo(BigDecimal.ZERO) > 0) {
                CatalogSkuSummaryDto sku = requireActiveSku(variantId);
                skuCache.put(variantId, sku);
                if (!order.isReserveStockAfterDeposit()) {
                    if (!inventoryApi.checkAvailability(variantId, delta)) {
                        throw new AppException(SalesErrorCode.INSUFFICIENT_STOCK, sku.sku());
                    }
                    inventoryApi.reserveStock(variantId, delta);
                }
            } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
                releaseReservedSafely(order, variantId, delta.abs());
            }
        }

        // Thu thập tất cả OrderItem hiện có trước khi xóa SalesInvoiceItem cũ
        List<OrderItem> existingOrderItems = orderItemRepository.findByOrderId(order.getOrderId());
        Map<UUID, OrderItem> orderItemByVariant = new LinkedHashMap<>();
        for (OrderItem oi : existingOrderItems) {
            orderItemByVariant.putIfAbsent(oi.getVariantId(), oi);
        }

        // Xóa SalesInvoiceItem cũ của hóa đơn này trước để tránh vướng FK ON DELETE RESTRICT nếu có OrderItem bị gỡ
        salesInvoiceItemRepository.deleteByInvoiceId(invoiceId);
        salesInvoiceItemRepository.flush();

        BigDecimal vatRate = SalesInvoiceType.PROJECT.name().equalsIgnoreCase(invoice.getInvoiceType())
                ? resolveOrderVatRate(order)
                : BigDecimal.ZERO;

        List<SalesInvoiceItem> savedInvoiceItems = new ArrayList<>();
        BigDecimal newInvoiceSubtotal = BigDecimal.ZERO;

        for (MergedLine line : newLines.values()) {
            UUID variantId = line.variantId();
            OrderItem targetOrderItem = orderItemByVariant.get(variantId);
            CatalogSkuSummaryDto sku = skuCache.get(variantId);
            if (sku == null) {
                Optional<CatalogSkuSummaryDto> opt = catalogApi.getSkuSummary(variantId);
                if (opt.isPresent()) {
                    sku = opt.get();
                } else if (targetOrderItem == null) {
                    throw new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId);
                }
            }

            // Giữ nguyên đơn giá thỏa thuận của đơn nếu SKU đã có trong đơn; SKU mới lấy giá từ Catalog
            BigDecimal unitPrice = targetOrderItem != null && targetOrderItem.getUnitPrice() != null
                    ? targetOrderItem.getUnitPrice()
                    : oldUnitPriceByVariant.getOrDefault(variantId,
                            sku != null ? safeAmount(sku.sellingPrice()) : BigDecimal.ZERO);

            BigDecimal newTotalVariantQty = otherQtyByVariant.getOrDefault(variantId, BigDecimal.ZERO).add(line.quantity());
            BigDecimal newTotalVariantDiscount = otherDiscountByVariant.getOrDefault(variantId, BigDecimal.ZERO)
                    .add(line.discountAmount());
            BigDecimal newOrderItemTotal = unitPrice.multiply(newTotalVariantQty).subtract(newTotalVariantDiscount)
                    .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);

            if (targetOrderItem == null) {
                if (sku == null) {
                    throw new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId);
                }
                targetOrderItem = orderItemRepository.saveAndFlush(OrderItem.builder()
                        .orderItemId(UUID.randomUUID())
                        .orderId(order.getOrderId())
                        .variantId(variantId)
                        .sku(sku.sku())
                        .productName(sku.productName() != null ? sku.productName() : sku.variantName())
                        .variantName(sku.variantName())
                        .quantity(newTotalVariantQty)
                        .unitPrice(unitPrice)
                        .discountAmount(newTotalVariantDiscount)
                        .lineTotal(newOrderItemTotal)
                        .build());
                orderItemByVariant.put(variantId, targetOrderItem);
            } else {
                targetOrderItem.setQuantity(newTotalVariantQty);
                targetOrderItem.setDiscountAmount(newTotalVariantDiscount);
                targetOrderItem.setLineTotal(newOrderItemTotal);
                targetOrderItem = orderItemRepository.saveAndFlush(targetOrderItem);
            }

            BigDecimal invLineTotal = unitPrice.multiply(line.quantity()).subtract(line.discountAmount())
                    .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            newInvoiceSubtotal = newInvoiceSubtotal.add(invLineTotal);

            String description = buildDescription(
                    targetOrderItem.getProductName(),
                    targetOrderItem.getVariantName());

            savedInvoiceItems.add(SalesInvoiceItem.builder()
                    .invoiceItemId(UUID.randomUUID())
                    .invoiceId(invoiceId)
                    .orderItemId(targetOrderItem.getOrderItemId())
                    .variantId(variantId)
                    .description(description)
                    .quantity(line.quantity())
                    .unitPrice(unitPrice)
                    .discountAmount(line.discountAmount())
                    .taxRate(vatRate)
                    .lineTotal(invLineTotal)
                    .build());
        }

        salesInvoiceItemRepository.saveAllAndFlush(savedInvoiceItems);

        // Xử lý các OrderItem bị gỡ khỏi hóa đơn này (nếu không còn ở đợt khác -> xóa an toàn)
        List<UUID> allNonThisInvoiceIds = allOrderInvoices.stream()
                .map(SalesInvoice::getInvoiceId)
                .filter(id -> !id.equals(invoiceId))
                .toList();

        for (UUID oldVariantId : oldQtyByVariant.keySet()) {
            if (newLines.containsKey(oldVariantId)) {
                continue;
            }
            OrderItem oi = orderItemByVariant.get(oldVariantId);
            if (oi == null) {
                continue;
            }
            BigDecimal remainingQty = otherQtyByVariant.getOrDefault(oldVariantId, BigDecimal.ZERO);
            if (remainingQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal remainingDiscount = otherDiscountByVariant.getOrDefault(oldVariantId, BigDecimal.ZERO);
                BigDecimal unitPrice = safeAmount(oi.getUnitPrice());
                oi.setQuantity(remainingQty);
                oi.setDiscountAmount(remainingDiscount);
                oi.setLineTotal(unitPrice.multiply(remainingQty).subtract(remainingDiscount)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO));
                orderItemRepository.saveAndFlush(oi);
            } else {
                boolean stillReferenced = !allNonThisInvoiceIds.isEmpty()
                        && salesInvoiceItemRepository.existsByOrderItemIdAndInvoiceIdIn(
                                oi.getOrderItemId(), allNonThisInvoiceIds);
                if (!stillReferenced) {
                    orderItemRepository.delete(oi);
                    orderItemRepository.flush();
                }
            }
        }

        // Cập nhật header hóa đơn DRAFT
        if (request.getBatchName() != null && !request.getBatchName().isBlank()) {
            invoice.setBatchName(request.getBatchName().trim());
        }
        if (request.getScheduledDeliveryDate() != null) {
            invoice.setScheduledDeliveryDate(request.getScheduledDeliveryDate());
        }
        if (request.getNote() != null) {
            invoice.setNote(trimToNull(request.getNote()));
        }
        BigDecimal invDiscount = request.getDiscountAmount() != null
                ? request.getDiscountAmount()
                : safeAmount(invoice.getDiscountAmount());
        BigDecimal taxBase = newInvoiceSubtotal.subtract(invDiscount).max(BigDecimal.ZERO);
        BigDecimal taxAmount = calculateTax(taxBase, vatRate);
        BigDecimal invTotal = taxBase.add(taxAmount);

        invoice.setSubtotal(newInvoiceSubtotal);
        invoice.setDiscountAmount(invDiscount);
        invoice.setTaxAmount(taxAmount);
        invoice.setTotalAmount(invTotal);
        salesInvoiceRepository.saveAndFlush(invoice);

        recalculateOrderTotalsAndPaymentFloor(order);
        log.info("Updated DRAFT invoice {} (order {}): subtotal={}, total={}",
                invoice.getInvoiceCode(), order.getOrderCode(), newInvoiceSubtotal, invTotal);

        return getInvoiceDetail(invoiceId);
    }

    @Override
    @Transactional
    public SalesInvoiceResponse issueInvoice(UUID invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new AppException(SalesErrorCode.INVOICE_NOT_FOUND, invoiceId));
        Order order = orderRepository.findByIdForUpdate(invoice.getOrderId())
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        ensureInvoiceEditableStatus(invoice, order);
        ensureNotBlockedByUc26(order.getOrderId());

        List<SalesInvoiceItem> items = salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(invoiceId);
        if (items.isEmpty()) {
            throw new AppException(SalesErrorCode.INVOICE_ITEMS_EMPTY);
        }

        OffsetDateTime now = OffsetDateTime.now();
        UUID actorId = resolveCurrentActorId(order.getSalesStaffId());
        int termDays = resolveClampedCreditTermDays(order, invoice);
        LocalDate dueDate = LocalDate.now().plusDays(termDays);

        invoice.setStatus(SalesInvoiceStatus.ISSUED.name());
        invoice.setIssuedBy(actorId);
        invoice.setIssuedAt(now);
        invoice.setIssueDate(now);
        invoice.setSignedAt(now);
        invoice.setCreditTermDays(termDays);
        invoice.setDueDate(dueDate);
        salesInvoiceRepository.saveAndFlush(invoice);

        // Chụp nhanh danh sách item cho sự kiện phát hành (Fulfillment lắng nghe để lập phiếu xuất kho)
        List<SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto> itemDtos = items.stream()
                .map(it -> new SalesInvoiceSummaryDto.SalesInvoiceItemSummaryDto(
                        it.getInvoiceItemId(),
                        it.getOrderItemId(),
                        it.getVariantId(),
                        it.getDescription(),
                        it.getQuantity(),
                        it.getUnitPrice(),
                        it.getDiscountAmount(),
                        it.getLineTotal()))
                .toList();

        eventPublisher.publishEvent(new SalesInvoiceIssuedEvent(
                invoice.getInvoiceId(),
                invoice.getInvoiceCode(),
                order.getOrderId(),
                order.getOrderCode(),
                order.getCustomerId(),
                invoice.getInvoiceType(),
                invoice.getBatchNo(),
                invoice.getBatchName(),
                invoice.getTotalAmount(),
                invoice.getDueDate(),
                actorId,
                itemDtos
        ));

        log.info("Issued invoice {} (type={}, batch={}, order={}) by actor {}",
                invoice.getInvoiceCode(), invoice.getInvoiceType(), invoice.getBatchNo(), order.getOrderCode(), actorId);

        return getInvoiceDetail(invoiceId);
    }

    @Override
    @Transactional
    public void cancelDraftInvoice(UUID invoiceId) {
        SalesInvoice invoice = salesInvoiceRepository.findByInvoiceIdForUpdate(invoiceId)
                .orElseThrow(() -> new AppException(SalesErrorCode.INVOICE_NOT_FOUND, invoiceId));
        Order order = orderRepository.findByIdForUpdate(invoice.getOrderId())
                .orElseThrow(() -> new AppException(SalesErrorCode.ORDER_NOT_FOUND));

        ensureInvoiceEditableStatus(invoice, order);
        ensureNotBlockedByUc26(order.getOrderId());

        long activeCount = salesInvoiceRepository.countByOrderIdAndStatusNot(
                order.getOrderId(), SalesInvoiceStatus.CANCELLED.name());
        if (activeCount <= 1) {
            throw new AppException(SalesErrorCode.INVOICE_CANNOT_DELETE_ONLY_BATCH);
        }

        List<SalesInvoiceItem> batchItems = salesInvoiceItemRepository.findByInvoiceIdOrderByInvoiceItemIdAsc(invoiceId);
        Map<UUID, BigDecimal> batchQtyByVariant = new LinkedHashMap<>();
        Map<UUID, BigDecimal> batchDiscountByVariant = new LinkedHashMap<>();
        for (SalesInvoiceItem item : batchItems) {
            batchQtyByVariant.merge(item.getVariantId(), safeAmount(item.getQuantity()), BigDecimal::add);
            batchDiscountByVariant.merge(item.getVariantId(), safeAmount(item.getDiscountAmount()), BigDecimal::add);
            releaseReservedSafely(order, item.getVariantId(), item.getQuantity());
        }

        // Xóa chi tiết hóa đơn nháp bị hủy trước để giải phóng FK trên order_items
        salesInvoiceItemRepository.deleteByInvoiceId(invoiceId);
        salesInvoiceItemRepository.flush();

        OffsetDateTime now = OffsetDateTime.now();
        invoice.setStatus(SalesInvoiceStatus.CANCELLED.name());
        invoice.setCancelledBy(resolveCurrentActorId(order.getSalesStaffId()));
        invoice.setCancelledAt(now);
        salesInvoiceRepository.saveAndFlush(invoice);

        // Đồng bộ lại số lượng OrderItem theo các hóa đơn active còn lại
        List<UUID> remainingInvoiceIds = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(order.getOrderId())
                .stream()
                .filter(inv -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(inv.getStatus()))
                .map(SalesInvoice::getInvoiceId)
                .toList();

        List<SalesInvoiceItem> remainingItems = remainingInvoiceIds.isEmpty()
                ? List.of()
                : salesInvoiceItemRepository.findByInvoiceIdIn(remainingInvoiceIds);
        Map<UUID, BigDecimal> remainingQtyByVariant = new LinkedHashMap<>();
        Map<UUID, BigDecimal> remainingDiscountByVariant = new LinkedHashMap<>();
        for (SalesInvoiceItem item : remainingItems) {
            remainingQtyByVariant.merge(item.getVariantId(), safeAmount(item.getQuantity()), BigDecimal::add);
            remainingDiscountByVariant.merge(item.getVariantId(), safeAmount(item.getDiscountAmount()), BigDecimal::add);
        }

        List<OrderItem> orderItems = orderItemRepository.findByOrderId(order.getOrderId());
        for (OrderItem oi : orderItems) {
            if (!batchQtyByVariant.containsKey(oi.getVariantId())) {
                continue;
            }
            BigDecimal newQty = remainingQtyByVariant.getOrDefault(oi.getVariantId(), BigDecimal.ZERO);
            if (newQty.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal newDisc = remainingDiscountByVariant.getOrDefault(oi.getVariantId(), BigDecimal.ZERO);
                oi.setQuantity(newQty);
                oi.setDiscountAmount(newDisc);
                oi.setLineTotal(safeAmount(oi.getUnitPrice()).multiply(newQty).subtract(newDisc)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO));
                orderItemRepository.saveAndFlush(oi);
            } else {
                boolean stillReferenced = !remainingInvoiceIds.isEmpty()
                        && salesInvoiceItemRepository.existsByOrderItemIdAndInvoiceIdIn(oi.getOrderItemId(), remainingInvoiceIds);
                if (!stillReferenced) {
                    orderItemRepository.delete(oi);
                    orderItemRepository.flush();
                }
            }
        }

        recalculateOrderTotalsAndPaymentFloor(order);
        log.info("Cancelled DRAFT invoice {} on order {}", invoice.getInvoiceCode(), order.getOrderCode());
    }

    // =========================================================================
    // PRIVATE HELPERS
    // =========================================================================

    private SalesInvoiceResponse createBatchBySplittingDraft(
            Order order,
            CreateDraftInvoiceRequest request,
            Map<UUID, MergedLine> mergedLines,
            int nextBatchNo,
            BigDecimal vatRate,
            OffsetDateTime now,
            UUID actorId,
            UUID newInvoiceId
    ) {
        SalesInvoice sourceInvoice = salesInvoiceRepository.findByInvoiceIdForUpdate(request.getSplitFromInvoiceId())
                .orElseThrow(() -> new AppException(SalesErrorCode.INVOICE_NOT_FOUND, request.getSplitFromInvoiceId()));
        if (!sourceInvoice.getOrderId().equals(order.getOrderId())) {
            throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID, "Hóa đơn nguồn không thuộc đơn hàng này");
        }
        if (!SalesInvoiceStatus.DRAFT.name().equalsIgnoreCase(sourceInvoice.getStatus())) {
            throw new AppException(SalesErrorCode.INVOICE_NOT_EDITABLE, sourceInvoice.getStatus());
        }

        List<SalesInvoiceItem> sourceItems = salesInvoiceItemRepository
                .findByInvoiceIdOrderByInvoiceItemIdAsc(sourceInvoice.getInvoiceId());
        Map<UUID, SalesInvoiceItem> sourceByVariant = sourceItems.stream()
                .collect(Collectors.toMap(SalesInvoiceItem::getVariantId, it -> it, (a, b) -> a, LinkedHashMap::new));

        List<SalesInvoiceItem> newBatchItems = new ArrayList<>();
        List<SalesInvoiceItem> sourceItemsToSave = new ArrayList<>();
        List<SalesInvoiceItem> sourceItemsToDelete = new ArrayList<>();
        BigDecimal newBatchSubtotal = BigDecimal.ZERO;

        for (MergedLine splitLine : mergedLines.values()) {
            SalesInvoiceItem src = sourceByVariant.get(splitLine.variantId());
            if (src == null || safeAmount(src.getQuantity()).compareTo(splitLine.quantity()) < 0) {
                throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID,
                        "Số lượng tách cho SKU " + splitLine.variantId() + " vượt quá số lượng đang có trên hóa đơn nguồn");
            }
            BigDecimal origQty = src.getQuantity();
            BigDecimal remainQty = origQty.subtract(splitLine.quantity());
            BigDecimal unitPrice = safeAmount(src.getUnitPrice());
            BigDecimal origDiscount = safeAmount(src.getDiscountAmount());

            BigDecimal movedDiscount = splitLine.discountAmount().compareTo(BigDecimal.ZERO) > 0
                    ? splitLine.discountAmount().min(origDiscount)
                    : (origDiscount.compareTo(BigDecimal.ZERO) > 0
                            ? origDiscount.multiply(splitLine.quantity()).divide(origQty, 2, RoundingMode.HALF_UP)
                            : BigDecimal.ZERO);
            BigDecimal remainDiscount = origDiscount.subtract(movedDiscount).max(BigDecimal.ZERO);

            BigDecimal movedLineTotal = unitPrice.multiply(splitLine.quantity()).subtract(movedDiscount)
                    .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO);
            newBatchSubtotal = newBatchSubtotal.add(movedLineTotal);

            newBatchItems.add(SalesInvoiceItem.builder()
                    .invoiceItemId(UUID.randomUUID())
                    .invoiceId(newInvoiceId)
                    .orderItemId(src.getOrderItemId())
                    .variantId(src.getVariantId())
                    .description(src.getDescription())
                    .quantity(splitLine.quantity())
                    .unitPrice(unitPrice)
                    .discountAmount(movedDiscount)
                    .taxRate(src.getTaxRate() != null ? src.getTaxRate() : vatRate)
                    .lineTotal(movedLineTotal)
                    .build());

            if (remainQty.compareTo(BigDecimal.ZERO) == 0) {
                sourceItemsToDelete.add(src);
            } else {
                src.setQuantity(remainQty);
                src.setDiscountAmount(remainDiscount);
                src.setLineTotal(unitPrice.multiply(remainQty).subtract(remainDiscount)
                        .setScale(2, RoundingMode.HALF_UP).max(BigDecimal.ZERO));
                sourceItemsToSave.add(src);
            }
        }

        // Đảm bảo hóa đơn nguồn còn ít nhất 1 sản phẩm
        int remainingLineCount = sourceItems.size() - sourceItemsToDelete.size();
        if (remainingLineCount <= 0) {
            throw new AppException(SalesErrorCode.INVOICE_SPLIT_INVALID,
                    "Không thể tách hết toàn bộ sản phẩm khỏi hóa đơn nguồn; hóa đơn nguồn phải giữ lại ít nhất 1 dòng hàng");
        }

        if (!sourceItemsToDelete.isEmpty()) {
            salesInvoiceItemRepository.deleteAll(sourceItemsToDelete);
        }
        if (!sourceItemsToSave.isEmpty()) {
            salesInvoiceItemRepository.saveAll(sourceItemsToSave);
        }
        salesInvoiceItemRepository.flush();

        List<SalesInvoiceItem> refreshedSource = salesInvoiceItemRepository
                .findByInvoiceIdOrderByInvoiceItemIdAsc(sourceInvoice.getInvoiceId());
        BigDecimal srcSubtotal = refreshedSource.stream()
                .map(SalesInvoiceItem::getLineTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal srcVat = SalesInvoiceType.PROJECT.name().equalsIgnoreCase(sourceInvoice.getInvoiceType())
                ? vatRate : BigDecimal.ZERO;
        BigDecimal srcTaxBase = srcSubtotal.subtract(safeAmount(sourceInvoice.getDiscountAmount())).max(BigDecimal.ZERO);
        BigDecimal srcTax = calculateTax(srcTaxBase, srcVat);
        sourceInvoice.setSubtotal(srcSubtotal);
        sourceInvoice.setTaxAmount(srcTax);
        sourceInvoice.setTotalAmount(srcTaxBase.add(srcTax));
        salesInvoiceRepository.saveAndFlush(sourceInvoice);

        BigDecimal newBatchTax = calculateTax(newBatchSubtotal, vatRate);
        String batchName = request.getBatchName() != null && !request.getBatchName().isBlank()
                ? request.getBatchName().trim()
                : "Đợt " + nextBatchNo;

        SalesInvoice created = salesInvoiceRepository.saveAndFlush(SalesInvoice.builder()
                .invoiceId(newInvoiceId)
                .invoiceCode(SalesInvoiceDraftInitializer.buildInvoiceCode(order, nextBatchNo))
                .orderId(order.getOrderId())
                .customerId(order.getCustomerId())
                .invoiceType(SalesInvoiceType.PROJECT.name())
                .batchNo(nextBatchNo)
                .batchName(batchName)
                .scheduledDeliveryDate(request.getScheduledDeliveryDate())
                .note(trimToNull(request.getNote()))
                .issueDate(now)
                .subtotal(newBatchSubtotal)
                .discountAmount(BigDecimal.ZERO)
                .taxAmount(newBatchTax)
                .totalAmount(newBatchSubtotal.add(newBatchTax))
                .status(SalesInvoiceStatus.DRAFT.name())
                .createdBy(actorId)
                .createdAt(now)
                .build());
        salesInvoiceItemRepository.saveAllAndFlush(newBatchItems);

        return getInvoiceDetail(created.getInvoiceId());
    }

    private void recalculateOrderTotalsAndPaymentFloor(Order order) {
        List<SalesInvoice> activeInvoices = salesInvoiceRepository.findByOrderIdOrderByBatchNoAscCreatedAtAsc(order.getOrderId())
                .stream()
                .filter(inv -> !SalesInvoiceStatus.CANCELLED.name().equalsIgnoreCase(inv.getStatus()))
                .toList();
        List<OrderItem> currentOrderItems = orderItemRepository.findByOrderId(order.getOrderId());

        BigDecimal sumInvoiceTotal = activeInvoices.stream()
                .map(inv -> safeAmount(inv.getTotalAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal sumInvoiceDiscounts = activeInvoices.stream()
                .map(inv -> safeAmount(inv.getDiscountAmount()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal shipping = safeAmount(order.getShippingFee());

        BigDecimal newOrderTotal = sumInvoiceTotal.add(shipping);

        if (OrderType.RETAIL.name().equalsIgnoreCase(order.getOrderType())) {
            BigDecimal sumOrderItemLineTotals = currentOrderItems.stream()
                    .map(oi -> safeAmount(oi.getLineTotal()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotal(sumOrderItemLineTotals);
            order.setDiscountAmount(sumInvoiceDiscounts);
        } else {
            BigDecimal sumGross = currentOrderItems.stream()
                    .map(oi -> safeAmount(oi.getUnitPrice()).multiply(safeAmount(oi.getQuantity()))
                            .setScale(2, RoundingMode.HALF_UP))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal sumItemDiscounts = currentOrderItems.stream()
                    .map(oi -> safeAmount(oi.getDiscountAmount()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            order.setSubtotal(sumGross);
            order.setDiscountAmount(sumItemDiscounts.add(sumInvoiceDiscounts));
        }

        // Chặn giảm tổng đơn xuống thấp hơn số tiền khách đã thanh toán xác minh (VERIFIED)
        Optional<OrderReceivableView> receivableOpt = orderReceivableApi.getOrderReceivable(order.getOrderId());
        if (receivableOpt.isPresent()) {
            BigDecimal verifiedPaid = safeAmount(receivableOpt.get().verifiedPaid());
            if (newOrderTotal.compareTo(verifiedPaid) < 0) {
                throw new AppException(SalesErrorCode.INVOICE_TOTAL_BELOW_PAID,
                        newOrderTotal.toPlainString(), verifiedPaid.toPlainString());
            }
            if (verifiedPaid.compareTo(BigDecimal.ZERO) > 0) {
                order.setPaymentStatus(verifiedPaid.compareTo(newOrderTotal) >= 0
                        ? PaymentStatus.PAID.name()
                        : PaymentStatus.PARTIALLY_PAID.name());
            } else {
                order.setPaymentStatus(PaymentStatus.UNPAID.name());
            }
        }

        order.setTotalAmount(newOrderTotal);
        if (order.getDepositPercent() != null && order.getDepositPercent().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal newDeposit = newOrderTotal.multiply(order.getDepositPercent())
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            order.setDepositAmount(newDeposit);
        }
        if (order.getRemainingDebtAmount() != null || order.getDepositAmount() != null) {
            BigDecimal dep = safeAmount(order.getDepositAmount());
            order.setRemainingDebtAmount(newOrderTotal.subtract(dep).max(BigDecimal.ZERO));
        }
        order.setUpdatedAt(OffsetDateTime.now());
        orderRepository.saveAndFlush(order);
    }

    private void ensureOrderAllowsInvoiceMutation(Order order) {
        if (OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus())
                || OrderStatus.COMPLETED.name().equalsIgnoreCase(order.getOrderStatus())) {
            throw new AppException(SalesErrorCode.ORDER_NOT_MODIFIABLE);
        }
    }

    private void ensureInvoiceEditableStatus(SalesInvoice invoice, Order order) {
        ensureOrderAllowsInvoiceMutation(order);
        if (!SalesInvoiceStatus.DRAFT.name().equalsIgnoreCase(invoice.getStatus())) {
            throw new AppException(SalesErrorCode.INVOICE_NOT_EDITABLE, invoice.getStatus());
        }
    }

    private void ensureNotBlockedByUc26(UUID orderId) {
        if (hasActiveUc26(orderId)) {
            throw new AppException(SalesErrorCode.INVOICE_MODIFICATION_BLOCKED_BY_UC26);
        }
    }

    private boolean hasActiveUc26(UUID orderId) {
        return orderModificationRequestRepository.existsByOrderIdAndRequestStatusIn(orderId, ACTIVE_UC26_STATUSES);
    }

    private CatalogSkuSummaryDto requireActiveSku(UUID variantId) {
        CatalogSkuSummaryDto sku = catalogApi.getSkuSummary(variantId)
                .orElseThrow(() -> new AppException(SalesErrorCode.SKU_NOT_FOUND, variantId));
        if (sku.status() != null && !"ACTIVE".equalsIgnoreCase(sku.status())) {
            throw new AppException(SalesErrorCode.SKU_NOT_ACTIVE, sku.sku());
        }
        return sku;
    }

    private void releaseReservedSafely(Order order, UUID variantId, BigDecimal qtyToRelease) {
        if (order.isReserveStockAfterDeposit() || qtyToRelease == null || qtyToRelease.compareTo(BigDecimal.ZERO) <= 0) {
            return;
        }
        try {
            Optional<StockSummaryDto> stockOpt = inventoryApi.getStockSummary(variantId);
            if (stockOpt.isPresent() && stockOpt.get().reservedQty() != null) {
                BigDecimal reserved = stockOpt.get().reservedQty();
                BigDecimal actualRelease = qtyToRelease.min(reserved);
                if (actualRelease.compareTo(BigDecimal.ZERO) > 0) {
                    inventoryApi.releaseReservedStock(variantId, actualRelease);
                }
            } else {
                inventoryApi.releaseReservedStock(variantId, qtyToRelease);
            }
        } catch (Exception ex) {
            log.warn("Could not release reserved stock for variant {}: {}", variantId, ex.getMessage());
        }
    }

    private int resolveClampedCreditTermDays(Order order, SalesInvoice invoice) {
        Integer raw = invoice.getCreditTermDays() != null ? invoice.getCreditTermDays() : order.getCreditTermDays();
        if (raw == null || raw <= 0) {
            return 0;
        }
        return Math.min(raw, 30);
    }

    private BigDecimal resolveOrderVatRate(Order order) {
        return order.getVatRate() != null && order.getVatRate().compareTo(BigDecimal.ZERO) > 0
                ? order.getVatRate()
                : BigDecimal.ZERO;
    }

    private BigDecimal calculateTax(BigDecimal taxBase, BigDecimal vatRate) {
        if (vatRate == null || vatRate.compareTo(BigDecimal.ZERO) <= 0
                || taxBase == null || taxBase.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }
        return taxBase.multiply(vatRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }

    private UUID resolveCurrentActorId(UUID fallbackStaffId) {
        try {
            UUID current = iamApi.getCurrentUserId();
            if (current != null) {
                return current;
            }
        } catch (Exception e) {
            log.debug("Fallback actorId used during invoice operation: {}", e.getMessage());
        }
        if (fallbackStaffId != null) {
            return fallbackStaffId;
        }
        throw new AppException(SalesErrorCode.ACTOR_REQUIRED);
    }

    private Map<UUID, MergedLine> mergeCreateLines(List<CreateDraftInvoiceRequest.InvoiceLineInput> lines) {
        Map<UUID, MergedLine> merged = new LinkedHashMap<>();
        for (CreateDraftInvoiceRequest.InvoiceLineInput line : lines) {
            BigDecimal disc = safeAmount(line.getDiscountAmount());
            merged.merge(line.getVariantId(),
                    new MergedLine(line.getVariantId(), line.getQuantity(), disc),
                    (a, b) -> new MergedLine(a.variantId(), a.quantity().add(b.quantity()),
                            a.discountAmount().add(b.discountAmount())));
        }
        return merged;
    }

    private Map<UUID, MergedLine> mergeUpdateLines(List<UpdateSalesInvoiceRequest.UpdateInvoiceItemInput> lines) {
        Map<UUID, MergedLine> merged = new LinkedHashMap<>();
        for (UpdateSalesInvoiceRequest.UpdateInvoiceItemInput line : lines) {
            BigDecimal disc = safeAmount(line.getDiscountAmount());
            merged.merge(line.getVariantId(),
                    new MergedLine(line.getVariantId(), line.getQuantity(), disc),
                    (a, b) -> new MergedLine(a.variantId(), a.quantity().add(b.quantity()),
                            a.discountAmount().add(b.discountAmount())));
        }
        return merged;
    }

    private SalesInvoiceResponse toResponse(
            SalesInvoice invoice,
            Order order,
            List<SalesInvoiceItem> items,
            Map<UUID, OrderItem> orderItemMap,
            boolean lockedByUc26
    ) {
        boolean orderMutable = !OrderStatus.CANCELLED.name().equalsIgnoreCase(order.getOrderStatus())
                && !OrderStatus.COMPLETED.name().equalsIgnoreCase(order.getOrderStatus());
        boolean editable = SalesInvoiceStatus.DRAFT.name().equalsIgnoreCase(invoice.getStatus())
                && orderMutable
                && !lockedByUc26;

        List<SalesInvoiceResponse.SalesInvoiceItemResponse> mappedItems = items.stream()
                .sorted(Comparator.comparing(SalesInvoiceItem::getInvoiceItemId))
                .map(it -> {
                    OrderItem oi = orderItemMap.get(it.getOrderItemId());
                    String sku = oi != null ? oi.getSku() : null;
                    if (sku == null) {
                        sku = catalogApi.getSkuSummary(it.getVariantId()).map(CatalogSkuSummaryDto::sku).orElse(null);
                    }
                    return SalesInvoiceResponse.SalesInvoiceItemResponse.builder()
                            .invoiceItemId(it.getInvoiceItemId())
                            .orderItemId(it.getOrderItemId())
                            .variantId(it.getVariantId())
                            .sku(sku)
                            .description(it.getDescription())
                            .quantity(it.getQuantity())
                            .unitPrice(it.getUnitPrice())
                            .discountAmount(it.getDiscountAmount())
                            .taxRate(it.getTaxRate())
                            .lineTotal(it.getLineTotal())
                            .build();
                })
                .toList();

        return SalesInvoiceResponse.builder()
                .invoiceId(invoice.getInvoiceId())
                .invoiceCode(invoice.getInvoiceCode())
                .orderId(invoice.getOrderId())
                .orderCode(order.getOrderCode())
                .customerId(invoice.getCustomerId())
                .invoiceType(invoice.getInvoiceType())
                .status(invoice.getStatus())
                .batchNo(invoice.getBatchNo())
                .batchName(invoice.getBatchName())
                .scheduledDeliveryDate(invoice.getScheduledDeliveryDate())
                .issueDate(invoice.getIssueDate())
                .creditTermDays(invoice.getCreditTermDays())
                .dueDate(invoice.getDueDate())
                .subtotal(invoice.getSubtotal())
                .discountAmount(invoice.getDiscountAmount())
                .taxAmount(invoice.getTaxAmount())
                .totalAmount(invoice.getTotalAmount())
                .createdBy(invoice.getCreatedBy())
                .createdAt(invoice.getCreatedAt())
                .issuedBy(invoice.getIssuedBy())
                .issuedAt(invoice.getIssuedAt())
                .cancelledBy(invoice.getCancelledBy())
                .cancelledAt(invoice.getCancelledAt())
                .note(invoice.getNote())
                .editable(editable)
                .lockedByModificationRequest(lockedByUc26)
                .items(mappedItems)
                .build();
    }

    private static BigDecimal safeAmount(BigDecimal value) {
        return value != null ? value : BigDecimal.ZERO;
    }

    private static String buildDescription(String productName, String variantName) {
        if (productName != null && variantName != null && !variantName.isBlank()) {
            return productName + " - " + variantName;
        }
        return productName != null ? productName : (variantName != null ? variantName : "");
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record MergedLine(UUID variantId, BigDecimal quantity, BigDecimal discountAmount) {
    }
}

