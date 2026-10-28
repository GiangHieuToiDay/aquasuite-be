package com.aquasuite.ims.module.catalog.internal.service;

import com.aquasuite.ims.module.inventory.api.InventoryApi;
import com.aquasuite.ims.module.inventory.api.dto.StockSummaryDto;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BundleInventoryReader {
    private final InventoryApi inventory;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<UUID, StockSummaryDto> read(Collection<UUID> ids) {
        return inventory.getStockSummaries(ids);
    }
}
