package com.aquasuite.ims.module.servicedesk.internal.repository;

import com.aquasuite.ims.module.servicedesk.internal.entity.WarrantyRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface WarrantyRecordRepository extends JpaRepository<WarrantyRecord, UUID> {
    List<WarrantyRecord> findByCustomerIdOrderByStartDateDesc(UUID customerId);
}
