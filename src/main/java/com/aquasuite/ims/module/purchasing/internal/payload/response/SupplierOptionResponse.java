package com.aquasuite.ims.module.purchasing.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SupplierOptionResponse {
    private UUID supplierId;
    private String supplierCode;
    private String supplierName;
    private String contactName;
    private String phone;
    private String email;
    private String address;
}