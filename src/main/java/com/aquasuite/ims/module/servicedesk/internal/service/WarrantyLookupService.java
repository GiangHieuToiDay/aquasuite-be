package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyLookupResponse;

import java.util.List;

/** UC-48 read-only customer warranty lookup. */
public interface WarrantyLookupService {
    List<WarrantyLookupResponse> findMine(String keyword);
}
