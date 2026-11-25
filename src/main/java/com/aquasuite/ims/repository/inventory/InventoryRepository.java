package com.aquasuite.ims.repository.inventory;

import com.aquasuite.ims.entity.inventory.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, UUID> {

    Optional<Inventory> findByVariantId(UUID variantId);

    List<Inventory> findAllByVariantId(UUID variantId);

    Optional<Inventory> findByVariantIdAndWarehouseCode(UUID variantId, String warehouseCode);

    List<Inventory> findByVariantIdIn(Collection<UUID> variantIds);

    List<Inventory> findByVariantIdInAndWarehouseCode(Collection<UUID> variantIds, String warehouseCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId")
    Optional<Inventory> findByVariantIdForUpdate(@Param("variantId") UUID variantId);

    /** Khóa dòng tồn theo đúng (variant, kho) để cộng tồn an toàn khi nhập kho đồng thời. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId AND i.warehouseCode = :warehouseCode")
    Optional<Inventory> findByVariantIdAndWarehouseCodeForUpdate(
            @Param("variantId") UUID variantId,
            @Param("warehouseCode") String warehouseCode);

    /**
     * Tạo dòng tồn rỗng cho (variant, kho) nếu chưa có, an toàn khi nhiều giao dịch cùng tạo lần đầu
     * (unique (variant_id, warehouse_code)). Gọi trước {@link #findByVariantIdAndWarehouseCodeForUpdate}.
     */
    @Modifying
    @Transactional(propagation = Propagation.MANDATORY)
    @Query(value = """
            INSERT INTO public.inventories
                (inventory_id, variant_id, warehouse_code, on_hand_qty, reserved_qty, minimum_qty, updated_at)
            VALUES (:inventoryId, :variantId, :warehouseCode, 0, 0, 0, CURRENT_TIMESTAMP)
            ON CONFLICT (variant_id, warehouse_code) DO NOTHING
            """, nativeQuery = true)
    int insertEmptyIfAbsent(@Param("inventoryId") UUID inventoryId,
                            @Param("variantId") UUID variantId,
                            @Param("warehouseCode") String warehouseCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.variantId = :variantId")
    List<Inventory> findAllByVariantIdForUpdate(@Param("variantId") UUID variantId);

    void deleteByVariantId(UUID variantId);

    @Query("SELECT i.variantId FROM Inventory i WHERE i.variantId IN :variantIds")
    Set<UUID> findExistingVariantIds(@Param("variantIds") Collection<UUID> variantIds);

    /**
     * Tra cứu tồn kho có phân trang cho UC-31, lọc theo trạng thái và cờ tồn thấp.
     * Trạng thái được suy ra ngay trong câu truy vấn từ tồn khả dụng ({@code onHandQty - reservedQty}) và {@code minimumQty}:
     * {@code OUT_OF_STOCK} khi khả dụng &le; 0; {@code LOW_STOCK} khi có ngưỡng và khả dụng &le; ngưỡng (vẫn &gt; 0);
     * {@code IN_STOCK} cho phần còn lại. Truyền {@code status = null} để bỏ lọc trạng thái.
     *
     * @param lowStockOnly chỉ lấy các SKU đang ở mức tồn thấp
     * @param status       mã trạng thái cần lọc ('IN_STOCK' | 'LOW_STOCK' | 'OUT_OF_STOCK') hoặc null
     */
    @Query("""
            SELECT i FROM Inventory i
            WHERE (:lowStockOnly = false
                   OR (i.minimumQty > 0 AND (i.onHandQty - i.reservedQty) <= i.minimumQty))
              AND (:status IS NULL
                   OR (:status = 'OUT_OF_STOCK' AND (i.onHandQty - i.reservedQty) <= 0)
                   OR (:status = 'LOW_STOCK' AND i.minimumQty > 0
                        AND (i.onHandQty - i.reservedQty) > 0
                        AND (i.onHandQty - i.reservedQty) <= i.minimumQty)
                   OR (:status = 'IN_STOCK' AND (i.onHandQty - i.reservedQty) > 0
                        AND (i.minimumQty = 0 OR (i.onHandQty - i.reservedQty) > i.minimumQty)))
            """)
    Page<Inventory> search(@Param("lowStockOnly") boolean lowStockOnly,
                           @Param("status") String status,
                           Pageable pageable);

    /**
     * Giống {@link #search(boolean, String, Pageable)} nhưng giới hạn trong tập {@code variantIds}.
     * Dùng khi người dùng nhập từ khóa: tập biến thể khớp từ khóa được phân giải từ Catalog trước,
     * rồi truyền vào đây để phân trang chính xác ở tầng CSDL.
     */
    @Query("""
            SELECT i FROM Inventory i
            WHERE i.variantId IN :variantIds
              AND (:lowStockOnly = false
                   OR (i.minimumQty > 0 AND (i.onHandQty - i.reservedQty) <= i.minimumQty))
              AND (:status IS NULL
                   OR (:status = 'OUT_OF_STOCK' AND (i.onHandQty - i.reservedQty) <= 0)
                   OR (:status = 'LOW_STOCK' AND i.minimumQty > 0
                        AND (i.onHandQty - i.reservedQty) > 0
                        AND (i.onHandQty - i.reservedQty) <= i.minimumQty)
                   OR (:status = 'IN_STOCK' AND (i.onHandQty - i.reservedQty) > 0
                        AND (i.minimumQty = 0 OR (i.onHandQty - i.reservedQty) > i.minimumQty)))
            """)
    Page<Inventory> searchByVariantIds(@Param("variantIds") Collection<UUID> variantIds,
                                       @Param("lowStockOnly") boolean lowStockOnly,
                                       @Param("status") String status,
                                       Pageable pageable);
}
