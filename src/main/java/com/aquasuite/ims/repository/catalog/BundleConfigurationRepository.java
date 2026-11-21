package com.aquasuite.ims.repository.catalog;

import com.aquasuite.ims.entity.catalog.BundleConfiguration;

import jakarta.persistence.LockModeType;

import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.util.*;

public interface BundleConfigurationRepository extends JpaRepository<BundleConfiguration, UUID> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from BundleConfiguration c where c.bundleProductId=:id")
    Optional<BundleConfiguration> lock(@Param("id") UUID id);
}
