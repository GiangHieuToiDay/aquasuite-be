package com.aquasuite.ims.module.audit.internal.repository;

import com.aquasuite.ims.module.audit.internal.entity.AuditEntry;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AuditEntryRepository extends JpaRepository<AuditEntry, UUID> {}
