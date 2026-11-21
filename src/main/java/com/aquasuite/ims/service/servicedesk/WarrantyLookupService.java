package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;

import java.util.List;

/** UC-48 read-only customer warranty lookup. */
public interface WarrantyLookupService {
    List<WarrantyLookupResponse> findMine(String keyword);
}
