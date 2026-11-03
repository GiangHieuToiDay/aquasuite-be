package com.aquasuite.ims.module.iam.internal.repository;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PermissionRepository extends JpaRepository<Permission, UUID> {

    Optional<Permission> findByCodeIgnoreCase(String code);

    boolean existsByCodeIgnoreCase(String code);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Permission p WHERE p.id = :id")
    Optional<Permission> findByIdForUpdate(@Param("id") UUID id);

    @Query(value = "SELECT COUNT(*) FROM public.role_permissions WHERE permission_id = :permissionId", nativeQuery = true)
    long countRoleAssignmentsByPermissionId(@Param("permissionId") UUID permissionId);

    @Query("SELECT p FROM Permission p WHERE LOWER(p.code) LIKE LOWER(CONCAT('%', :search, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :search, '%'))")
    Page<Permission> searchPermissions(@Param("search") String search, Pageable pageable);
}
