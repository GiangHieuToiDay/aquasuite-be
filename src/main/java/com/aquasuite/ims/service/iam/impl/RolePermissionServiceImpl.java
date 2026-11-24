package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.common.PageQuery;
import com.aquasuite.ims.common.PageResponse;
import com.aquasuite.ims.dto.iam.request.CreatePermissionRequest;
import com.aquasuite.ims.dto.iam.request.UpdatePermissionRequest;
import com.aquasuite.ims.dto.iam.response.PermissionResponse;
import com.aquasuite.ims.dto.iam.response.RoleOptionResponse;
import com.aquasuite.ims.dto.iam.response.RoleResponse;
import com.aquasuite.ims.entity.iam.Permission;
import com.aquasuite.ims.entity.iam.Role;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.exception.CommonErrorCode;
import com.aquasuite.ims.exception.iam.IamErrorCode;
import com.aquasuite.ims.mapper.iam.PermissionMapper;
import com.aquasuite.ims.mapper.iam.RoleMapper;
import com.aquasuite.ims.repository.iam.PermissionRepository;
import com.aquasuite.ims.repository.iam.RoleRepository;
import com.aquasuite.ims.service.iam.RolePermissionService;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class RolePermissionServiceImpl implements RolePermissionService {

    public static final List<String> CANONICAL_ROLES_ORDER = List.of(
            "SHOP_OWNER", "SALES_STAFF", "ACCOUNTANT", "SHIPPER", "CUSTOMER"
    );

    private static final Set<String> CANONICAL_ROLES_SET = Set.copyOf(CANONICAL_ROLES_ORDER);
    private static final Pattern PERMISSION_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("code", "description", "id");
    private static final String UQ_PERMISSIONS_CODE_LOWER = "uq_permissions_code_lower";
    private static final String FK_ROLE_PERMISSIONS_PERMISSION = "fk_role_permissions_permission";

    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final RoleMapper roleMapper;
    private final PermissionMapper permissionMapper;

    @Autowired
    public RolePermissionServiceImpl(
            @Autowired(required = false) RoleRepository roleRepository,
            @Autowired(required = false) PermissionRepository permissionRepository,
            RoleMapper roleMapper,
            PermissionMapper permissionMapper) {
        this.roleRepository = roleRepository;
        this.permissionRepository = permissionRepository;
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    public RolePermissionServiceImpl(
            RoleRepository roleRepository,
            PermissionRepository permissionRepository,
            RoleMapper roleMapper) {
        this(roleRepository, permissionRepository, roleMapper, null);
    }

    public RolePermissionServiceImpl(
            PermissionRepository permissionRepository,
            PermissionMapper permissionMapper) {
        this(null, permissionRepository, null, permissionMapper);
    }

    // ── Roles (from IamRoleServiceImpl) ──────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public List<RoleOptionResponse> listAssignableRoles() {
        return roleRepository.findAll().stream()
                .filter(Role::isActive)
                .map(role -> RoleOptionResponse.builder()
                        .id(role.getRoleId())
                        .code(role.getRoleName())
                        .description(role.getDescription())
                        .build())
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<RoleResponse> listCanonicalRoles() {
        List<Role> roles = roleRepository.findAllWithPermissionsByRoleNameIn(CANONICAL_ROLES_SET);

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
                .map(roleMapper::toResponse)
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

        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse assignPermissionToRole(UUID roleId, UUID permissionId) {
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new AppException(IamErrorCode.ROLE_NOT_FOUND));

        if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
            log.error("Invariant violation: attempted to assign permission to non-canonical role '{}'", role.getRoleName());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

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

        return roleMapper.toResponse(role);
    }

    @Override
    @Transactional
    public RoleResponse unassignPermissionFromRole(UUID roleId, UUID permissionId) {
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        Role role = roleRepository.findByIdForUpdate(roleId)
                .orElseThrow(() -> new AppException(IamErrorCode.ROLE_NOT_FOUND));

        if (!CANONICAL_ROLES_SET.contains(role.getRoleName())) {
            log.error("Invariant violation: attempted to unassign permission from non-canonical role '{}'", role.getRoleName());
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        boolean removed = role.getPermissions().removeIf(p -> p.getId().equals(permissionId));
        if (removed) {
            roleRepository.saveAndFlush(role);
            log.info("Unassigned permission '{}' ({}) from role '{}' ({})",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        } else {
            log.info("Permission '{}' ({}) was not assigned to role '{}' ({}) - idempotent noop",
                    permission.getCode(), permissionId, role.getRoleName(), roleId);
        }

        return roleMapper.toResponse(role);
    }

    // ── Permissions (from IamPermissionServiceImpl) ──────────────────────────

    @Override
    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> listPermissions(PageQuery pageQuery, String search) {
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();
        String sortBy = pageQuery.getSortBy();
        String direction = pageQuery.getDirection();
        if (page < 0) {
            log.warn("Invalid page number: {}", page);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }
        if (size < 1 || size > 100) {
            log.warn("Invalid page size: {}", size);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String sortField = (sortBy == null || sortBy.isBlank()) ? "code" : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(sortField)) {
            log.warn("Invalid sortBy field: {}", sortBy);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String dir = (direction == null || direction.isBlank()) ? "asc" : direction.trim().toLowerCase(Locale.ROOT);
        if (!"asc".equals(dir) && !"desc".equals(dir)) {
            log.warn("Invalid sort direction: {}", direction);
            throw new AppException(CommonErrorCode.INVALID_REQUEST_PARAM);
        }

        String searchTerm = (search == null || search.trim().isEmpty()) ? null : search.trim();

        Sort sort = "desc".equals(dir) ? Sort.by(sortField).descending() : Sort.by(sortField).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        Page<Permission> pageResult = searchTerm == null
                ? permissionRepository.findAll(pageable)
                : permissionRepository.searchPermissions(searchTerm, pageable);

        return PageResponse.from(pageResult, permissionMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionDetail(UUID permissionId) {
        Permission perm = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));
        return permissionMapper.toResponse(perm);
    }

    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new AppException(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT);
        }

        String normalizedCode = request.getCode().trim().toUpperCase(Locale.ROOT);

        if (!PERMISSION_CODE_PATTERN.matcher(normalizedCode).matches() || normalizedCode.length() > 150) {
            log.warn("Permission code '{}' failed format validation", normalizedCode);
            throw new AppException(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT);
        }

        if (normalizedCode.startsWith(ROLE_PREFIX)) {
            log.warn("Permission code '{}' uses forbidden ROLE_ prefix", normalizedCode);
            throw new AppException(IamErrorCode.RESERVED_ROLE_NAMESPACE);
        }

        String description = cleanDescription(request.getDescription());

        if (permissionRepository.existsByCodeIgnoreCase(normalizedCode)) {
            log.warn("Permission code '{}' already exists", normalizedCode);
            throw new AppException(IamErrorCode.PERMISSION_CODE_DUPLICATE);
        }

        Permission permission = new Permission();
        permission.setId(UUID.randomUUID());
        permission.setCode(normalizedCode);
        permission.setDescription(description);

        try {
            permission = permissionRepository.saveAndFlush(permission);
            log.info("Created permission '{}' ({})", permission.getCode(), permission.getId());
        } catch (DataIntegrityViolationException ex) {
            if (isPermissionCodeUniqueViolation(ex)) {
                log.warn("Concurrent duplicate on permission creation for code '{}': {}", normalizedCode, ex.getMessage());
                throw new AppException(IamErrorCode.PERMISSION_CODE_DUPLICATE);
            }
            log.error("Unhandled integrity violation on permission creation for code '{}': {}", normalizedCode, ex.getMessage(), ex);
            throw ex;
        }

        return permissionMapper.toResponse(permission);
    }

    @Override
    @Transactional
    public PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        String description = cleanDescription(request.getDescription());

        permission.setDescription(description);
        permission = permissionRepository.saveAndFlush(permission);
        log.info("Updated description for permission '{}' ({})", permission.getCode(), permission.getId());

        return permissionMapper.toResponse(permission);
    }

    @Override
    @Transactional
    public void deletePermission(UUID permissionId) {
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        long assignedCount = permissionRepository.countRoleAssignmentsByPermissionId(permissionId);
        if (assignedCount > 0) {
            log.warn("Cannot delete permission '{}' ({}): currently assigned to {} role(s)",
                    permission.getCode(), permissionId, assignedCount);
            throw new AppException(IamErrorCode.PERMISSION_CURRENTLY_ASSIGNED);
        }

        try {
            permissionRepository.delete(permission);
            permissionRepository.flush();
            log.info("Deleted unused permission '{}' ({})", permission.getCode(), permissionId);
        } catch (DataIntegrityViolationException ex) {
            if (isRolePermissionForeignKeyViolation(ex)) {
                log.warn("Race condition: assigned-role FK violation while deleting permission '{}' ({}): {}",
                        permission.getCode(), permissionId, ex.getMessage());
                throw new AppException(IamErrorCode.PERMISSION_CURRENTLY_ASSIGNED);
            }
            log.error("Unhandled integrity violation while deleting permission '{}' ({}): {}",
                    permission.getCode(), permissionId, ex.getMessage(), ex);
            throw ex;
        }
    }

    private boolean isPermissionCodeUniqueViolation(DataIntegrityViolationException ex) {
        String constraintName = extractExactConstraintName(ex);
        return UQ_PERMISSIONS_CODE_LOWER.equals(constraintName);
    }

    private boolean isRolePermissionForeignKeyViolation(DataIntegrityViolationException ex) {
        String constraintName = extractExactConstraintName(ex);
        return FK_ROLE_PERMISSIONS_PERMISSION.equals(constraintName);
    }

    private String extractExactConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && !name.isBlank()) {
                    return normalizeConstraintName(name);
                }
            }
            String psqlConstraint = extractFromPsqlException(current);
            if (psqlConstraint != null && !psqlConstraint.isBlank()) {
                return normalizeConstraintName(psqlConstraint);
            }
            current = current.getCause();
        }
        return null;
    }

    private String extractFromPsqlException(Throwable throwable) {
        if (throwable != null && "org.postgresql.util.PSQLException".equals(throwable.getClass().getName())) {
            try {
                java.lang.reflect.Method getMsgMethod = throwable.getClass().getMethod("getServerErrorMessage");
                Object serverMsg = getMsgMethod.invoke(throwable);
                if (serverMsg != null) {
                    java.lang.reflect.Method getConstraintMethod = serverMsg.getClass().getMethod("getConstraint");
                    Object constraint = getConstraintMethod.invoke(serverMsg);
                    if (constraint instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return null;
    }

    private String normalizeConstraintName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        if (cleaned.startsWith("\"") && cleaned.endsWith("\"") && cleaned.length() >= 2) {
            cleaned = cleaned.substring(1, cleaned.length() - 1);
        }
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            cleaned = cleaned.substring(lastDot + 1);
        }
        return cleaned.toLowerCase(Locale.ROOT);
    }

    private String cleanDescription(String rawDescription) {
        if (rawDescription == null) {
            return null;
        }
        String trimmed = rawDescription.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > 500) {
            throw new AppException(CommonErrorCode.VALIDATION_ERROR);
        }
        return trimmed;
    }
}
