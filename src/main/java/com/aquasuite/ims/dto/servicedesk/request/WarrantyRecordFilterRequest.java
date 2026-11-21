package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.enums.servicedesk.WarrantyDisplayStatus;
import com.aquasuite.ims.common.PageQuery;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class WarrantyRecordFilterRequest extends PageQuery {
    private String keyword;
    private String phone;
    private String serialNumber;
    private WarrantyDisplayStatus displayStatus;
    private UUID customerId;
    private UUID orderId;
    private UUID variantId;
}

