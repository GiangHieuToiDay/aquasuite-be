package com.aquasuite.ims.module.iam.internal.authorization.repository;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);
}
