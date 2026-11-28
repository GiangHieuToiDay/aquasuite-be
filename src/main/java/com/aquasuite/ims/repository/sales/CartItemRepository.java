package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.entity.sales.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository truy vấn và thao tác bảng cart_items theo phạm vi khách hàng (UC-17).
 */
public interface CartItemRepository extends JpaRepository<CartItem, UUID> {

    List<CartItem> findAllByCustomerIdOrderByUpdatedAtDesc(UUID customerId);

    Optional<CartItem> findByCustomerIdAndVariantId(UUID customerId, UUID variantId);

    Optional<CartItem> findByCustomerIdAndComboId(UUID customerId, UUID comboId);

    Optional<CartItem> findByCartItemIdAndCustomerId(UUID cartItemId, UUID customerId);

    void deleteAllByCustomerId(UUID customerId);
}

