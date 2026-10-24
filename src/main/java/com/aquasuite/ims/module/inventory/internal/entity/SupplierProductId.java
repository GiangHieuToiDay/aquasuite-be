package com.aquasuite.ims.module.inventory.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierProductId implements Serializable {

    @Column(name = "supplier_id", nullable = false)
    private UUID supplierId;

    @Column(name = "product_id", nullable = false)
    private UUID productId;

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SupplierProductId that = (SupplierProductId) o;
        return Objects.equals(supplierId, that.supplierId)
                && Objects.equals(productId, that.productId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(supplierId, productId);
    }
}
