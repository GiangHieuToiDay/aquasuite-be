package com.aquasuite.ims.module.sales.internal.service.helper;

import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDisplayItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderItemResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

public final class OrderDisplayItems {
    private OrderDisplayItems() { }

    public static List<SalesOrderDisplayItemResponse> build(List<OrderItem> items,
            List<SalesOrderItemResponse> mapped, List<QuotationItem> quotationItems) {
        Map<UUID, SalesOrderItemResponse> responses = mapped.stream().collect(
                Collectors.toMap(SalesOrderItemResponse::getOrderItemId, i -> i));
        Map<UUID, List<OrderItem>> groups = new LinkedHashMap<>();
        for (OrderItem item : items) {
            UUID key = item.getComboId() == null ? item.getOrderItemId()
                    : item.getComboGroupId() != null ? item.getComboGroupId() : item.getComboId();
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(item);
        }
        List<SalesOrderDisplayItemResponse> result = new ArrayList<>();
        for (List<OrderItem> group : groups.values()) {
            OrderItem first = group.getFirst();
            if (first.getComboId() == null) {
                result.add(new SalesOrderDisplayItemResponse("SINGLE", null, first.getProductName(),
                        first.getQuantity(), first.getLineTotal(), first.getSku(), first.getUnitPrice(),
                        first.getDiscountAmount(), responses.get(first.getOrderItemId()), List.of()));
                continue;
            }
            BigDecimal quantity = first.getComboQuantity();
            String sku = first.getComboSku();
            BigDecimal unitPrice = first.getComboUnitPrice();
            BigDecimal gross = null;
            List<QuotationItem> roots = quotationItems.stream()
                    .filter(q -> first.getComboId().equals(q.getComboId()) && q.getVariantId() == null
                            && q.getParentItemId() == null && q.getQuantity() != null
                            && (first.getComboGroupId() == null || first.getComboGroupId().equals(q.getQuotationItemId())))
                    .toList();
            if (quantity == null) {
                if (!roots.isEmpty()) quantity = roots.stream().map(QuotationItem::getQuantity)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
            }
            if (sku == null && !roots.isEmpty()) sku = roots.getFirst().getSku();
            if (unitPrice != null && quantity != null) gross = unitPrice.multiply(quantity);
            if (unitPrice == null && quantity != null && quantity.signum() > 0
                    && !roots.isEmpty() && roots.stream().allMatch(q -> q.getUnitPrice() != null)) {
                gross = roots.stream().map(q -> q.getQuantity().multiply(q.getUnitPrice()))
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                unitPrice = gross.divide(quantity, 2, RoundingMode.HALF_UP);
            }
            BigDecimal net = group.stream().map(OrderItem::getLineTotal).reduce(BigDecimal.ZERO, BigDecimal::add);
            result.add(new SalesOrderDisplayItemResponse("COMBO", first.getComboId(), first.getProductName(),
                    quantity, net, sku, unitPrice, gross == null ? null : gross.subtract(net),
                    null, group.stream().map(i -> responses.get(i.getOrderItemId())).toList()));
        }
        return result;
    }
}
