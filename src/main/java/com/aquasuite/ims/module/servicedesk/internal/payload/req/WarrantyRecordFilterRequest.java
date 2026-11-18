package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyDisplayStatus;
import com.aquasuite.ims.share.dto.PageQuery;
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

