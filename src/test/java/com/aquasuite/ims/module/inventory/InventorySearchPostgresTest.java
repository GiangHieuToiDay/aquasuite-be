package com.aquasuite.ims.module.inventory;

import com.aquasuite.ims.module.inventory.internal.entity.Inventory;
import com.aquasuite.ims.module.inventory.internal.repository.InventoryRepository;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test chạy trên PostgreSQL thật (Testcontainers) cho truy vấn tra cứu tồn kho UC-31.
 * Kiểm chứng logic suy ra trạng thái và lọc tồn thấp trong câu JPQL — phần không thể phủ bằng mock.
 * Yêu cầu Docker đang chạy (hoặc biến môi trường TEST_DATABASE_*).
 */
@DataJpaTest(showSql = false)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class InventorySearchPostgresTest extends PostgreSqlIntegrationTestSupport {

    @Autowired private InventoryRepository inventories;

    private UUID inStock;      // available 50, min 5  -> IN_STOCK
    private UUID lowStock;     // available 5,  min 5  -> LOW_STOCK
    private UUID outOfStock;   // available 0,  min 2  -> OUT_OF_STOCK (vẫn tính là tồn thấp vì có ngưỡng)
    private UUID noThreshold;  // available 8,  min 0  -> IN_STOCK, không theo dõi ngưỡng

    private final Pageable pageable = PageRequest.of(0, 20);

    @BeforeEach
    void seed() {
        inventories.deleteAll();
        inStock = save(new BigDecimal("50"), BigDecimal.ZERO, new BigDecimal("5"));
        lowStock = save(new BigDecimal("5"), BigDecimal.ZERO, new BigDecimal("5"));
        outOfStock = save(new BigDecimal("3"), new BigDecimal("3"), new BigDecimal("2"));
        noThreshold = save(new BigDecimal("10"), new BigDecimal("2"), BigDecimal.ZERO);
    }

    private UUID save(BigDecimal onHand, BigDecimal reserved, BigDecimal min) {
        UUID variantId = UUID.randomUUID();
        inventories.saveAndFlush(Inventory.builder()
                .inventoryId(UUID.randomUUID())
                .variantId(variantId)
                .warehouseCode("WH_MAIN")
                .onHandQty(onHand)
                .reservedQty(reserved)
                .minimumQty(min)
                .build());
        return variantId;
    }

    private Set<UUID> variantIds(Page<Inventory> page) {
        return page.getContent().stream().map(Inventory::getVariantId).collect(java.util.stream.Collectors.toSet());
    }

    @Test
    @DisplayName("search không lọc -> trả về toàn bộ SKU")
    void searchAll() {
        assertThat(variantIds(inventories.search(false, null, pageable)))
                .containsExactlyInAnyOrder(inStock, lowStock, outOfStock, noThreshold);
    }

    @Test
    @DisplayName("lowStockOnly=true -> chỉ các SKU có ngưỡng và tồn khả dụng <= ngưỡng")
    void searchLowStockOnly() {
        assertThat(variantIds(inventories.search(true, null, pageable)))
                .containsExactlyInAnyOrder(lowStock, outOfStock)
                .doesNotContain(inStock, noThreshold);
    }

    @Test
    @DisplayName("status=LOW_STOCK -> còn hàng nhưng chạm ngưỡng (loại trừ hết hàng)")
    void filterLowStockStatus() {
        assertThat(variantIds(inventories.search(false, "LOW_STOCK", pageable)))
                .containsExactly(lowStock);
    }

    @Test
    @DisplayName("status=OUT_OF_STOCK -> tồn khả dụng <= 0")
    void filterOutOfStock() {
        assertThat(variantIds(inventories.search(false, "OUT_OF_STOCK", pageable)))
                .containsExactly(outOfStock);
    }

    @Test
    @DisplayName("status=IN_STOCK -> còn hàng và chưa chạm ngưỡng (gồm cả SKU không cài ngưỡng)")
    void filterInStock() {
        assertThat(variantIds(inventories.search(false, "IN_STOCK", pageable)))
                .containsExactlyInAnyOrder(inStock, noThreshold);
    }

    @Test
    @DisplayName("searchByVariantIds giới hạn đúng trong tập truyền vào")
    void searchByVariantIds() {
        Page<Inventory> page = inventories.searchByVariantIds(List.of(inStock, lowStock), false, null, pageable);
        assertThat(variantIds(page)).containsExactlyInAnyOrder(inStock, lowStock);
    }
}
