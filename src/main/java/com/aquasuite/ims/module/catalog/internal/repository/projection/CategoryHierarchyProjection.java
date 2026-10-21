package com.aquasuite.ims.module.catalog.internal.repository.projection;

import java.util.UUID;

public interface CategoryHierarchyProjection {
    UUID getCategoryId();
    UUID getParentId();
}
