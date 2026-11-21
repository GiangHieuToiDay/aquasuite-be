package com.aquasuite.ims.dto.servicedesk.request;

import com.aquasuite.ims.enums.servicedesk.WarrantyTicketStatus;
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

