package com.aquasuite.ims.module.servicedesk.internal.payload.req;

import com.aquasuite.ims.module.servicedesk.internal.enums.WarrantyTicketStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WarrantyTicketFilterRequest {
    private WarrantyTicketStatus status;
    private Integer page;
    private Integer size;
}

