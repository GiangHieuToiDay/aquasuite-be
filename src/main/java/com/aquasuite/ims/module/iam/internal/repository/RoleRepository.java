package com.aquasuite.ims.module.iam.internal.repository;

import com.aquasuite.ims.module.iam.internal.entity.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleNameIgnoreCase(String roleName);

    boolean existsByRoleNameIgnoreCase(String roleName);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Role r WHERE r.roleId = :roleId")
    Optional<Role> findByIdForUpdate(@Param("roleId") UUID roleId);

    @Query("SELECT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.roleId = :roleId")
    Optional<Role> findWithPermissionsByRoleId(@Param("roleId") UUID roleId);

    @Query("SELECT DISTINCT r FROM Role r LEFT JOIN FETCH r.permissions WHERE r.roleName IN :roleNames")
    List<Role> findAllWithPermissionsByRoleNameIn(@Param("roleNames") Collection<String> roleNames);
}
