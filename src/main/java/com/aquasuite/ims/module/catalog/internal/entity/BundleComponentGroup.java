package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.util.UUID;

/**
 * Nhóm SKU cùng giá được phép thay cho một thành phần mặc định, giữ số lượng item gốc.
 * Là giá trị thuộc cấu hình combo, không tạo SKU hay bản ghi tồn kho riêng cho combo.
 */
@Embeddable
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class BundleComponentGroup {
    @Column(name = "group_id", nullable = false)
    private UUID groupId;
    @Column(name = "default_variant_id", nullable = false)
    private UUID defaultVariantId;
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "allowed_variant_ids", nullable = false, columnDefinition = "uuid[]")
    private UUID[] allowedVariantIds;
}
