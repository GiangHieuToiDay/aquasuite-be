package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.common.PageQuery;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class WarrantyRecordFilterRequest extends PageQuery {
    @Size(max = 100, message = "Từ khóa tìm kiếm tối đa 100 ký tự.")
    private String keyword;
    @Size(max = 32, message = "Số điện thoại tìm kiếm tối đa 32 ký tự.")
    private String phone;
    @Size(max = 100, message = "Số serial tìm kiếm tối đa 100 ký tự.")
    private String serialNumber;
    private WarrantyDisplayStatus displayStatus;
    private UUID customerId;
    private UUID orderId;
    private UUID variantId;
}

