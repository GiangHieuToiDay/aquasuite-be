package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import com.aquasuite.ims.module.sales.internal.mapper.SalesOrderMapper;
import com.aquasuite.ims.module.sales.internal.service.helper.OrderDisplayItems;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class OrderDisplayItemsTest {
    private final SalesOrderMapper mapper = Mappers.getMapper(SalesOrderMapper.class);

    private OrderItem item(UUID combo, UUID group, BigDecimal sets) {
        return OrderItem.builder().orderItemId(UUID.randomUUID()).variantId(UUID.randomUUID())
                .comboId(combo).comboGroupId(group).comboQuantity(sets).productName("Snapshot")
                .quantity(BigDecimal.valueOf(6)).lineTotal(BigDecimal.TEN).build();
    }

    @Test void groupsTwoSetsAndKeepsOrdinarySkuAndRepeatedComboSelectionsSeparate() {
        UUID combo = UUID.randomUUID(), group = UUID.randomUUID();
        var a = item(combo, group, BigDecimal.valueOf(2));
        var b = item(combo, group, BigDecimal.valueOf(2));
        a.setComboSku("COMBO-001");
        a.setComboUnitPrice(new BigDecimal("12"));
        var single = item(null, null, null);
        var other = item(combo, UUID.randomUUID(), BigDecimal.ONE);
        var items = List.of(a, single, b, other);
        var mapped = mapper.toItemResponseList(items);
        var rows = OrderDisplayItems.build(items, mapped, List.of());
        assertThat(rows).hasSize(3);
        assertThat(rows.getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(rows.getFirst().lineTotal()).isEqualByComparingTo("20");
        assertThat(rows.getFirst().sku()).isEqualTo("COMBO-001");
        assertThat(rows.getFirst().unitPrice()).isEqualByComparingTo("12");
        assertThat(rows.getFirst().discountAmount()).isEqualByComparingTo("4");
        assertThat(rows.getFirst().components()).containsExactly(mapped.get(0), mapped.get(2));
        assertThat(rows.get(1).item()).isSameAs(mapped.get(1));
        assertThat(rows.get(1).itemType()).isEqualTo("SINGLE");
        assertThat(rows.get(2).quantity()).isEqualByComparingTo("1");
        assertThat(a.getQuantity()).isEqualByComparingTo("6");
    }

    @Test void historicalQuotationUsesSavedSetsWithoutCatalogAndUnknownLegacyQuantityStaysNull() {
        UUID combo = UUID.randomUUID();
        var items = List.of(item(combo, null, null), item(combo, null, null));
        var root = QuotationItem.builder().comboId(combo).quantity(BigDecimal.valueOf(2))
                .sku("SAVED-COMBO").unitPrice(new BigDecimal("15")).build();
        var rows = OrderDisplayItems.build(items, mapper.toItemResponseList(items), List.of(root));
        assertThat(rows).hasSize(1);
        assertThat(rows.getFirst().quantity()).isEqualByComparingTo("2");
        assertThat(rows.getFirst().sku()).isEqualTo("SAVED-COMBO");
        assertThat(rows.getFirst().unitPrice()).isEqualByComparingTo("15");
        assertThat(rows.getFirst().discountAmount()).isEqualByComparingTo("10");
        assertThat(OrderDisplayItems.build(items, mapper.toItemResponseList(items), List.of())
                .getFirst().quantity()).isNull();
    }
}
