package com.aquasuite.ims.module.servicedesk.api;

import com.aquasuite.ims.module.servicedesk.api.dto.WarrantyEligibilityDto;

import java.util.UUID;

/**
 * Hợp đồng công khai cho các module khác kiểm tra tính hợp lệ của thẻ bảo hành (UC-45).
 */
public interface WarrantyApi {

    boolean isEligible(UUID warrantyRecordId);

    WarrantyEligibilityDto getEligibility(UUID warrantyRecordId);
}

