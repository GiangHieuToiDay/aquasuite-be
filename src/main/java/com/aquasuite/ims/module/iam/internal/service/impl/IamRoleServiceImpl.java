package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.error.IamErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.module.iam.internal.payload.response.RoleResponse;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.service.IamRoleService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
public class IamRoleServiceImpl implements IamRoleService {

    public static final List<String> CANONICAL_ROLES_ORDER = List.of(
            "SHOP_OWNER", "SALES_STAFF", "ACCOUNTANT", "TECHNICAL_STAFF", "SHIPPER", "CUSTOMER"
    );

    private static final Set<String> CANONICAL_ROLES_SET = Set.copyOf(CANONICAL_ROLES_ORDER);

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;

    public IamRoleServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleRepository roleRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PermissionRepository permissionRepository) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> listCanonicalRoles() {
        List<Role> roles = roleRepository.findAllWithPermissionsByRoleNameIn(CANONICAL_ROLES_SET);

        // Invariant check: all canonical roles must exist and no non-canonical role
        for (Role role : roles) {
            if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
                log.error("Invariant violation: non-canonical role '{}' detected", role.getRoleName());
                throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
            }
        }

        if (roles.size() != CANONICAL_ROLES_ORDER.size()) {
            log.error("Invariant violation: expected {} canonical roles, found {}", CANONICAL_ROLES_ORDER.size(), roles.size());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        return roles.stream()
                .sorted(Comparator.comparingInt(r -> CANONICAL_ROLES_ORDER.indexOf(r.getRoleName())))
                .map(this::toRoleResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public RoleResponse getRoleDetail(UUID roleId) {
        Role role = roleRepository.findWithPermissionsByRoleId(roleId)
                .orElseThrow(() -> new AppException(IamErrorCode.ROLE_NOT_FOUND));

        if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
            log.error("Invariant violation: requested role '{}' is not a canonical role", role.getRoleName());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        return toRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse assignPermissionToRole(UUID roleId, UUID permissionId) {
        // Lock Permission first
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        // Lock Role second
        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new AppException(IamErrorCode.ROLE_NOT_FOUND));

        if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
            log.error("Invariant violation: attempted to assign permission to non-canonical role '{}'", role.getRoleName());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        // Idempotent assignment
        boolean alreadyAssigned = role.getPermissions().stream()
                .anyMatch(p -> p.getId().equals(permissionId));

        if (!alreadyAssigned) {
            role.getPermissions().add(permission);
            roleRepository.saveAndFlush(role);
            log.info("Assigned permission '{}' ({}) to role '{}' ({})",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        } else {
            log.info("Permission '{}' ({}) is already assigned to role '{}' ({}) - idempotent noop",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        }

        return toRoleResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse unassignPermissionFromRole(UUID roleId, UUID permissionId) {
        // Lock Permission first
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        // Lock Role second
        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new AppException(IamErrorCode.ROLE_NOT_FOUND));

        if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
            log.error("Invariant violation: attempted to unassign permission from non-canonical role '{}'", role.getRoleName());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        // Idempotent unassignment
        boolean removed = role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        if (removed) {
            roleRepository.saveAndFlush(role);
            log.info("Unassigned permission '{}' ({}) from role '{}' ({})",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        } else {
            log.info("Permission '{}' ({}) was not assigned to role '{}' ({}) - idempotent noop",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        }

        return toRoleResponse(role);
    }

    private RoleResponse toRoleResponse(Role role) {
        List<PermissionResponse> permissions = role.getPermissions() == null ? List.of() :
                role.getPermissions().stream()
                        .map(p -> PermissionResponse.builder()
                                .id(p.getId())
                                .code(p.getCode())
                                .description(p.getDescription())
                                .build())
                        .sorted(Comparator.comparing(PermissionResponse::getCode))
                        .toList();

        return RoleResponse.builder()
                .id(role.getRoleId())
                .code(role.getRoleName())
                .description(role.getDescription())
                .active(role.isActive())
                .permissions(permissions)
                .build();
    }
}
