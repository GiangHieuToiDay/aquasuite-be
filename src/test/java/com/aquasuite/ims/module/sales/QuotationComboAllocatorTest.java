package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.calculator.QuotationComboAllocator;
import com.aquasuite.ims.module.sales.internal.entity.QuotationItem;
import org.junit.jupiter.api.Test;
import java.math.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class QuotationComboAllocatorTest {
    @Test void preservesExactNetAcrossRoundingDiscountsAndInputOrder() {
        var random = new Random(23);
        for (int sample=0; sample<100; sample++) {
            var root = QuotationItem.builder().quotationItemId(UUID.randomUUID()).comboId(UUID.randomUUID())
                    .quantity(BigDecimal.valueOf(1+random.nextInt(4))).discountRate(BigDecimal.valueOf(random.nextInt(101))).build();
            List<QuotationItem> items = new ArrayList<>();
            BigDecimal retail = BigDecimal.ZERO;
            for (int i=0; i<5; i++) {
                var child = QuotationItem.builder().quotationItemId(UUID.randomUUID()).parentItemId(root.getQuotationItemId())
                        .variantId(UUID.randomUUID()).quantity(root.getQuantity().multiply(BigDecimal.valueOf(i+1)))
                        .unitPrice(BigDecimal.valueOf(random.nextInt(10000),2)).build();
                items.add(child);
                retail = retail.add(child.getQuantity().multiply(child.getUnitPrice()));
            }
            root.setUnitPrice(retail.divide(root.getQuantity()).multiply(new BigDecimal("0.71")).setScale(2,RoundingMode.DOWN));
            items.add(root);
            var amounts = QuotationComboAllocator.allocate(items);
            BigDecimal gross = root.getQuantity().multiply(root.getUnitPrice()).setScale(2,RoundingMode.HALF_UP);
            BigDecimal net = gross.subtract(gross.multiply(root.getDiscountRate()).divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP));
            assertThat(amounts.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(net);
            for (var child : items) {
                if (child == root) continue;
                assertThat(amounts.get(child.getQuotationItemId())).isBetween(BigDecimal.ZERO,child.getQuantity().multiply(child.getUnitPrice()));
            }
            Collections.reverse(items);
            assertThat(QuotationComboAllocator.allocate(items)).isEqualTo(amounts);
        }
    }

    @Test void directPackageAllocationPreservesCentsAndStableRemainders() {
        UUID a = new UUID(0,1), b = new UUID(0,2), c = new UUID(0,3);
        Map<UUID,BigDecimal> weights = new LinkedHashMap<>();
        weights.put(c, BigDecimal.ONE);
        weights.put(b, BigDecimal.ONE);
        weights.put(a, BigDecimal.ONE);
        var allocated = QuotationComboAllocator.allocate(new BigDecimal("1.00"),weights);
        assertThat(allocated.get(a)).isEqualByComparingTo("0.34");
        assertThat(allocated.get(b)).isEqualByComparingTo("0.33");
        assertThat(allocated.get(c)).isEqualByComparingTo("0.33");
        assertThat(allocated.values().stream().reduce(BigDecimal.ZERO,BigDecimal::add))
                .isEqualByComparingTo("1.00");
        assertThat(QuotationComboAllocator.allocate(new BigDecimal("3.00"),weights).values())
                .allSatisfy(value -> assertThat(value).isEqualByComparingTo("1.00"));
        assertThatThrownBy(() -> QuotationComboAllocator.allocate(new BigDecimal("3.01"),weights))
                .isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
    }

    @Test void rejectsPackageWithoutComponentsAndLeavesNormalSkuAlone() {
        var normal = QuotationItem.builder().quotationItemId(UUID.randomUUID()).variantId(UUID.randomUUID()).build();
        assertThat(QuotationComboAllocator.allocate(List.of(normal))).isEmpty();
        var root = QuotationItem.builder().quotationItemId(UUID.randomUUID()).comboId(UUID.randomUUID()).build();
        assertThatThrownBy(() -> QuotationComboAllocator.allocate(List.of(root)))
                .isInstanceOf(com.aquasuite.ims.share.exception.AppException.class);
    }
}
