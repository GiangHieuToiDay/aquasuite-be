package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.*;

import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import org.hibernate.annotations.BatchSize;

@Entity
@Table(name = "bundle_configurations")
@Getter
@Setter
@NoArgsConstructor
public class BundleConfiguration {
    @Id
    @Column(name = "bundle_product_id")
    private UUID bundleProductId;

    @Column(name = "package_price", precision = 15, scale = 2)
    private BigDecimal packagePrice;

    // Lịch hiển thị dùng UTC, gồm mốc bắt đầu và không gồm mốc kết thúc; nháp có thể chưa có lịch.
    @Column(name = "valid_from")
    private Instant validFrom;

    @Column(name = "valid_until")
    private Instant validUntil;

    // Trạng thái công bố độc lập với ACTIVE/INACTIVE của Product; hiển thị phải thỏa cả hai.
    @Enumerated(EnumType.STRING)
    @Column(name = "publication_status", nullable = false, length = 20)
    private PublicationStatus publicationStatus = PublicationStatus.DRAFT;

    @Column(name = "stock_policy", nullable = false, length = 40)
    private String stockPolicy = "CHECK_COMPONENT_STOCK";

    // Nhóm option thuộc vòng đời cấu hình; tải theo lô để tránh N+1 khi lấy danh sách combo.
    @BatchSize(size = 100)
    @ElementCollection
    @CollectionTable(name = "bundle_component_groups", joinColumns = @JoinColumn(name = "bundle_product_id"))
    @OrderBy("groupId")
    private List<BundleComponentGroup> componentGroups = new ArrayList<>();

    @Version private Long version;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public enum PublicationStatus {
        DRAFT,
        PUBLISHED,
        UNPUBLISHED
    }
}
