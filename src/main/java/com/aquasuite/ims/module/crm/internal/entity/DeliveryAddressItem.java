package com.aquasuite.ims.module.crm.internal.entity;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * Model đại diện cho một bản ghi địa chỉ trong Sổ địa chỉ giao hàng (Delivery Address Book),
 * được lưu trực tiếp dưới dạng danh sách JSONB trong bảng customers thay vì bảng phụ riêng.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DeliveryAddressItem {

    private UUID addressId;
    private String recipientName;
    private String phone;
    private String province;
    private String district;
    private String ward;
    private String streetAddress;
    private String addressText;
    private String label;

    @Builder.Default
    private boolean isDefault = false;

    @Builder.Default
    private boolean isDeleted = false;

    private Instant createdAt;
    private Instant updatedAt;
}
