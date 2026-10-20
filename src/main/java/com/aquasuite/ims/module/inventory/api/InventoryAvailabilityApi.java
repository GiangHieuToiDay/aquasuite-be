package com.aquasuite.ims.module.inventory.api;
import java.math.BigDecimal;
import java.util.*;
/**
 * Catalog preview contract for the existing single warehouse. No actor required.
 * REQUIRES_NEW/read-only (isolates read failures from the Catalog transaction); values are onHand-reserved. Missing rows are omitted.
 * Dependency failures propagate to preview, which must return UNKNOWN.
 * Does not reserve stock. Additive API, legacy facade remains unchanged.
 */
public interface InventoryAvailabilityApi {
    Map<UUID, BigDecimal> availableQuantities(Collection<UUID> variantIds);
}
