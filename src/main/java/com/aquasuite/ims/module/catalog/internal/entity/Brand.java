package com.aquasuite.ims.module.catalog.internal.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "brands", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Brand {

    @Id
    @Column(name = "brand_id", nullable = false)
    private UUID brandId;

    @Column(name = "brand_code", nullable = false, length = 50)
    private String brandCode;

    @Column(name = "brand_name", nullable = false, length = 100)
    private String brandName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 32)
    private String status = "ACTIVE";
}
