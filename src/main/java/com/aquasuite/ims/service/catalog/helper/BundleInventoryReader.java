package com.aquasuite.ims.service.catalog.helper;

import com.aquasuite.ims.service.inventory.InventoryService;
import com.aquasuite.ims.dto.inventory.response.StockSummaryDto;

import lombok.RequiredArgsConstructor;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

import java.util.*;

@Service
@RequiredArgsConstructor
public class BundleInventoryReader {
    private final InventoryService inventory;

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Map<UUID, StockSummaryDto> read(Collection<UUID> ids) {
        return inventory.getStockSummaries(ids);
    }
}
