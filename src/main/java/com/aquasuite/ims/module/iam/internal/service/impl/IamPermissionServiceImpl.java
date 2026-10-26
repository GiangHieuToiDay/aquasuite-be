package com.aquasuite.ims.module.iam.internal.service.impl;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.error.IamErrorCode;
import com.aquasuite.ims.module.iam.internal.mapper.PermissionMapper;
import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.service.IamPermissionService;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import com.aquasuite.ims.share.dto.PageQuery;
import com.aquasuite.ims.share.dto.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class IamPermissionServiceImpl implements IamPermissionService {

    private static final Pattern PERMISSION_CODE_PATTERN = Pattern.compile("^[A-Z][A-Z0-9_]*$");
    private static final String ROLE_PREFIX = "ROLE_";
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("code", "description", "id");

    private final PermissionRepository permissionRepository;
    private final PermissionMapper permissionMapper;

    public IamPermissionServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) PermissionRepository permissionRepository,
            PermissionMapper permissionMapper) {
        this.permissionRepository = permissionRepository;
        this.permissionMapper = permissionMapper;
    }

    /** Tìm kiếm và phân trang quyền với trường sắp xếp được kiểm soát. */
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PermissionResponse> listPermissions(PageQuery pageQuery, String search) {
        int page = pageQuery.getPage();
        int size = pageQuery.getSize();
        String sortBy = pageQuery.getSortBy();
        String direction = pageQuery.getDirection();
        // Query param validation
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

        Page<Permission> pageResult = permissionRepository.searchPermissions(searchTerm, pageable);

        return PageResponse.from(pageResult, permissionMapper::toResponse);
    }

    /** Tải quyền theo ID hoặc phát sinh lỗi không tìm thấy. */
    @Override
    @Transactional(readOnly = true)
    public PermissionResponse getPermissionDetail(UUID permissionId) {
        Permission perm = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));
        return permissionMapper.toResponse(perm);
    }

    /** Chuẩn hóa mã, chặn namespace vai trò và tạo quyền mới. */
    @Override
    @Transactional
    public PermissionResponse createPermission(CreatePermissionRequest request) {
        if (request.getCode() == null || request.getCode().trim().isEmpty()) {
            throw new AppException(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT);
        }

        // Normalize code: trim and uppercase with Locale.ROOT
        String normalizedCode = request.getCode().trim().toUpperCase(Locale.ROOT);

        // Validate format: ^[A-Z][A-Z0-9_]*$ and length 1-150
        if (!PERMISSION_CODE_PATTERN.matcher(normalizedCode).matches() || normalizedCode.length() > 150) {
            log.warn("Permission code '{}' failed format validation", normalizedCode);
            throw new AppException(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT);
        }

        // Validate reserved ROLE_ namespace
        if (normalizedCode.startsWith(ROLE_PREFIX)) {
            log.warn("Permission code '{}' uses forbidden ROLE_ prefix", normalizedCode);
            throw new AppException(IamErrorCode.RESERVED_ROLE_NAMESPACE);
        }

        // Validate description
        String description = cleanDescription(request.getDescription());

        // Uniqueness check case-insensitive
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

    /** Cập nhật mô tả quyền trong khi giữ mã quyền bất biến. */
    @Override
    @Transactional
    public PermissionResponse updatePermission(UUID permissionId, UpdatePermissionRequest request) {
        Permission permission = permissionRepository.findById(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        String description = cleanDescription(request.getDescription());

        // Last-write-wins update on description only
        permission.setDescription(description);
        permission = permissionRepository.saveAndFlush(permission);
        log.info("Updated description for permission '{}' ({})", permission.getCode(), permission.getId());

        return permissionMapper.toResponse(permission);
    }

    /** Xóa quyền và chuyển lỗi ràng buộc vai trò thành lỗi nghiệp vụ. */
    @Override
    @Transactional
    public void deletePermission(UUID permissionId) {
        // Lock Permission row
        Permission permission = permissionRepository.findByIdForUpdate(permissionId)
                .orElseThrow(() -> new AppException(IamErrorCode.PERMISSION_NOT_FOUND));

        // Check if assigned to any role
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

    private static final String UQ_PERMISSIONS_CODE_LOWER = "uq_permissions_code_lower";
    private static final String FK_ROLE_PERMISSIONS_PERMISSION = "fk_role_permissions_permission";

    /** Xác định lỗi database do trùng mã quyền không phân biệt hoa thường. */
    private boolean isPermissionCodeUniqueViolation(DataIntegrityViolationException ex) {
        String constraintName = extractExactConstraintName(ex);
        return UQ_PERMISSIONS_CODE_LOWER.equals(constraintName);
    }

    /** Xác định lỗi xóa quyền vẫn đang được vai trò sử dụng. */
    private boolean isRolePermissionForeignKeyViolation(DataIntegrityViolationException ex) {
        String constraintName = extractExactConstraintName(ex);
        return FK_ROLE_PERMISSIONS_PERMISSION.equals(constraintName);
    }

    /** Lần theo chuỗi nguyên nhân để lấy chính xác tên constraint database. */
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

    /** Đọc tên constraint trực tiếp từ ngoại lệ PostgreSQL nếu có. */
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

    /** Chuẩn hóa tên constraint để so sánh ổn định giữa các driver. */
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

    /** Chuẩn hóa mô tả rỗng thành null. */
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
