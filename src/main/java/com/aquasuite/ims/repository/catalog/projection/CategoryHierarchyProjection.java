package com.aquasuite.ims.repository.catalog.projection;

import java.util.UUID;

public interface CategoryHierarchyProjection {
    UUID getCategoryId();
    UUID getParentId();
}
