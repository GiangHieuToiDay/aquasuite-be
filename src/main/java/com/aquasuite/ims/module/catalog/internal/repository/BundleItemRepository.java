package com.aquasuite.ims.module.catalog.internal.repository;

import com.aquasuite.ims.module.catalog.internal.entity.BundleItem;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.*;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {
    List<BundleItem> findByBundleProductIdOrderByBundleItemId(UUID id);

    List<BundleItem> findByBundleProductIdIn(Collection<UUID> ids);
}
