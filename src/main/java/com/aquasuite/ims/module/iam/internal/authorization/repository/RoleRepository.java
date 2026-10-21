package com.aquasuite.ims.module.iam.internal.authorization.repository;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface RoleRepository extends JpaRepository<Role, UUID> {

    Optional<Role> findByRoleNameIgnoreCase(String roleName);

    boolean existsByRoleNameIgnoreCase(String roleName);
}
