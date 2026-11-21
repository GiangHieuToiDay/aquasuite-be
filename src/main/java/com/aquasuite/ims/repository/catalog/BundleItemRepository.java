package com.aquasuite.ims.repository.catalog;

import com.aquasuite.ims.entity.catalog.BundleItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {
    List<BundleItem> findByBundleProductIdOrderByBundleItemId(UUID id);

    List<BundleItem> findByBundleProductIdIn(Collection<UUID> ids);
}
