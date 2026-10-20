package com.aquasuite.ims.module.crm.internal.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.Instant;
import java.util.UUID;

/** CRM customer profile. userId is a scalar reference to IAM, never a cross-module entity link. */
@Entity
@Table(name="customers",schema="public")
@Getter @Setter @NoArgsConstructor
public class Customer {
    @Id @Column(name="customer_id") private UUID id;
    @Column(name="user_id",nullable=false,unique=true) private UUID userId;
    @Column(name="customer_code",nullable=false,unique=true,length=50) private String code;
    @Column(name="customer_name",nullable=false,length=50) private String name;
    @Column(nullable=false,length=32) private String phone;
    @Column(nullable=false,length=320) private String email;
    @Column(name="customer_type",nullable=false,length=32) private String type;
    @Column(columnDefinition="TEXT") private String address;
    @Column(name="company_name",length=255) private String companyName;
    @Column(nullable=false,length=32) private String status;
    @Column(name="created_at",nullable=false,insertable=false,updatable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false,insertable=false) private Instant updatedAt;
}
