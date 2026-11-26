package com.aquasuite.ims.dto.purchasing.request;

import com.aquasuite.ims.enums.purchasing.PurchaseOrderStatus;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PurchaseOrderFilterRequest {

    @Size(max = 100, message = "Từ khóa tìm kiếm không được vượt quá 100 ký tự")
    private String search;
    private UUID supplierId;
    private PurchaseOrderStatus status;
    private LocalDate fromDate;
    private LocalDate toDate;
    @Builder.Default
    private Integer page = 0;
    @Builder.Default
    private Integer size = 10;
    @Builder.Default
    @Size(max = 64, message = "Trường sắp xếp không được vượt quá 64 ký tự")
    private String sortBy = "createdAt";
    @Builder.Default
    private String sortDirection = "DESC";
}