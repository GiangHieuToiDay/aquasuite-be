package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.error.IamErrorCode;
import com.aquasuite.ims.module.iam.internal.mapper.PermissionMapper;
import com.aquasuite.ims.module.iam.internal.mapper.RoleMapper;
import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.request.UpdatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.payload.response.PermissionResponse;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.IamPermissionServiceImpl;
import com.aquasuite.ims.module.iam.internal.service.impl.IamRoleServiceImpl;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.dao.DataIntegrityViolationException;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class IamValidationUnitTest {

    @Mock
    private PermissionRepository permissionRepository;

    @Mock
    private RoleRepository roleRepository;

    @Mock
    private PermissionMapper permissionMapper;

    @Mock
    private RoleMapper roleMapper;

    private IamPermissionServiceImpl permissionService;
    private IamRoleServiceImpl roleService;

    @BeforeEach
    void setUp() {
        permissionService = new IamPermissionServiceImpl(permissionRepository, permissionMapper);
        roleService = new IamRoleServiceImpl(roleRepository, permissionRepository, roleMapper);
    }

    @Test
    @DisplayName("AC-05: Normalizes code with trim and uppercase Locale.ROOT")
    void testNormalizeCode() {
        CreatePermissionRequest request = CreatePermissionRequest.builder()
                .code("   order_approve   ")
                .description("   Sample description   ")
                .build();

        when(permissionRepository.existsByCodeIgnoreCase("ORDER_APPROVE")).thenReturn(false);
        when(permissionRepository.saveAndFlush(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(permissionMapper.toResponse(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            return PermissionResponse.builder().id(p.getId()).code(p.getCode()).description(p.getDescription()).build();
        });

        PermissionResponse response = permissionService.createPermission(request);

        assertThat(response.getCode()).isEqualTo("ORDER_APPROVE");
        assertThat(response.getDescription()).isEqualTo("Sample description");
    }

    @Test
    @DisplayName("AC-05: Rejects null or blank permission code with IAM_001")
    void testRejectNullOrBlankCode() {
        CreatePermissionRequest req1 = CreatePermissionRequest.builder().code(null).build();
        assertThatThrownBy(() -> permissionService.createPermission(req1))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT));

        CreatePermissionRequest req2 = CreatePermissionRequest.builder().code("   ").build();
        assertThatThrownBy(() -> permissionService.createPermission(req2))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT));
    }

    @Test
    @DisplayName("AC-05, AC-07: Rejects codes not matching UPPER_SNAKE_CASE with IAM_001")
    void testRejectInvalidFormatCode() {
        List<String> invalidCodes = List.of(
                "123_INVALID",
                "_LEADING_UNDERSCORE",
                "INVALID-DASH",
                "INVALID.DOT",
                "INVALID SPACE",
                "A".repeat(151)
        );

        for (String invalid : invalidCodes) {
            CreatePermissionRequest req = CreatePermissionRequest.builder().code(invalid).build();
            assertThatThrownBy(() -> permissionService.createPermission(req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.INVALID_PERMISSION_CODE_FORMAT));
        }
    }

    @Test
    @DisplayName("AC-05, AC-08: Rejects permission code starting with ROLE_ prefix with IAM_002")
    void testRejectReservedRolePrefix() {
        List<String> rolePrefixedCodes = List.of(
                "ROLE_ADMIN",
                "role_manager",
                "  RoLe_UsEr  "
        );

        for (String code : rolePrefixedCodes) {
            CreatePermissionRequest req = CreatePermissionRequest.builder().code(code).build();
            assertThatThrownBy(() -> permissionService.createPermission(req))
                    .isInstanceOf(AppException.class)
                    .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.RESERVED_ROLE_NAMESPACE));
        }
    }

    @Test
    @DisplayName("AC-06: Duplicate permission code throws IAM_005")
    void testDuplicatePermissionCode() {
        CreatePermissionRequest req = CreatePermissionRequest.builder().code("ORDER_APPROVE").build();
        when(permissionRepository.existsByCodeIgnoreCase("ORDER_APPROVE")).thenReturn(true);

        assertThatThrownBy(() -> permissionService.createPermission(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.PERMISSION_CODE_DUPLICATE));
    }

    @Test
    @DisplayName("AC-07: Description length over 500 throws VALIDATION_ERROR")
    void testDescriptionLengthValidation() {
        CreatePermissionRequest req = CreatePermissionRequest.builder()
                .code("VALID_CODE")
                .description("A".repeat(501))
                .build();

        assertThatThrownBy(() -> permissionService.createPermission(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.VALIDATION_ERROR));
    }

    @Test
    @DisplayName("AC-07: Update permission only modifies description (last-write-wins)")
    void testUpdatePermissionDescription() {
        UUID permId = UUID.randomUUID();
        Permission existing = new Permission(permId, "ORDER_APPROVE", "Old description");

        when(permissionRepository.findById(permId)).thenReturn(Optional.of(existing));
        when(permissionRepository.saveAndFlush(any(Permission.class))).thenAnswer(inv -> inv.getArgument(0));
        when(permissionMapper.toResponse(any(Permission.class))).thenAnswer(inv -> {
            Permission p = inv.getArgument(0);
            return PermissionResponse.builder().id(p.getId()).code(p.getCode()).description(p.getDescription()).build();
        });

        UpdatePermissionRequest req = UpdatePermissionRequest.builder().description("New description").build();
        PermissionResponse updated = permissionService.updatePermission(permId, req);

        assertThat(updated.getCode()).isEqualTo("ORDER_APPROVE");
        assertThat(updated.getDescription()).isEqualTo("New description");
    }

    @Test
    @DisplayName("AC-04: Pagination validation rejects negative page, invalid size, non-allowlisted sort")
    void testPaginationValidation() {
        // Negative page
        assertThatThrownBy(() -> permissionService.listPermissions(-1, 20, null, "code", "asc"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM));

        // Size < 1
        assertThatThrownBy(() -> permissionService.listPermissions(0, 0, null, "code", "asc"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM));

        // Size > 100
        assertThatThrownBy(() -> permissionService.listPermissions(0, 101, null, "code", "asc"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM));

        // Invalid sortBy
        assertThatThrownBy(() -> permissionService.listPermissions(0, 20, null, "unsupported_field", "asc"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM));

        // Invalid direction
        assertThatThrownBy(() -> permissionService.listPermissions(0, 20, null, "code", "invalid_dir"))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.INVALID_REQUEST_PARAM));
    }

    @Test
    @DisplayName("EF-04: Non-canonical role in DB violates invariant and throws DATA_INTEGRITY_VIOLATION")
    void testNonCanonicalRoleInvariant() {
        Role nonCanonicalRole = new Role(UUID.randomUUID(), "SUPER_ADMIN", "Admin", true, null, Set.of());
        when(roleRepository.findAllWithPermissionsByRoleNameIn(any())).thenReturn(List.of(nonCanonicalRole));

        assertThatThrownBy(() -> roleService.listCanonicalRoles())
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(CommonErrorCode.DATA_INTEGRITY_VIOLATION));
    }

    private DataIntegrityViolationException createHibernateDive(String message, String constraintName) {
        SQLException sqlEx = new SQLException("DB error", "23505");
        ConstraintViolationException cve = new ConstraintViolationException(message, sqlEx, constraintName);
        return new DataIntegrityViolationException(message, cve);
    }

    private DataIntegrityViolationException createPsqlDive(String message, String constraintName) {
        PSQLException psqlEx = mock(PSQLException.class);
        ServerErrorMessage serverErrorMessage = mock(ServerErrorMessage.class);
        when(serverErrorMessage.getConstraint()).thenReturn(constraintName);
        when(psqlEx.getServerErrorMessage()).thenReturn(serverErrorMessage);
        return new DataIntegrityViolationException(message, psqlEx);
    }

    @Test
    @DisplayName("Finding 5 - Test 1: Exact duplicate-code constraint via Hibernate or PSQLException maps to IAM_005")
    void testExactDuplicateCodeConstraintMapsToIam005() {
        CreatePermissionRequest req = CreatePermissionRequest.builder().code("PERM_CREATE_INTEGRITY").build();
        when(permissionRepository.existsByCodeIgnoreCase("PERM_CREATE_INTEGRITY")).thenReturn(false);

        // 1a. Via Hibernate ConstraintViolationException
        DataIntegrityViolationException hibernateEx = createHibernateDive("error", "uq_permissions_code_lower");
        doThrow(hibernateEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.PERMISSION_CODE_DUPLICATE));

        // 1b. Via PSQLException metadata
        DataIntegrityViolationException psqlEx = createPsqlDive("error", "uq_permissions_code_lower");
        doThrow(psqlEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(req))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.PERMISSION_CODE_DUPLICATE));
    }

    @Test
    @DisplayName("Finding 5 - Test 2: Exact assigned-permission FK constraint via Hibernate or PSQLException maps to IAM_006")
    void testExactAssignedPermissionFkConstraintMapsToIam006() {
        UUID permId = UUID.randomUUID();
        Permission perm = new Permission(permId, "PERM_DELETE_INTEGRITY", "desc");
        when(permissionRepository.findByIdForUpdate(permId)).thenReturn(Optional.of(perm));
        when(permissionRepository.countRoleAssignmentsByPermissionId(permId)).thenReturn(0L);

        // 2a. Via Hibernate ConstraintViolationException
        DataIntegrityViolationException hibernateEx = createHibernateDive("error", "fk_role_permissions_permission");
        doThrow(hibernateEx).when(permissionRepository).flush();

        assertThatThrownBy(() -> permissionService.deletePermission(permId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.PERMISSION_CURRENTLY_ASSIGNED));

        // 2b. Via PSQLException metadata
        DataIntegrityViolationException psqlEx = createPsqlDive("error", "fk_role_permissions_permission");
        doThrow(psqlEx).when(permissionRepository).flush();

        assertThatThrownBy(() -> permissionService.deletePermission(permId))
                .isInstanceOf(AppException.class)
                .satisfies(ex -> assertThat(((AppException) ex).getErrorCode()).isEqualTo(IamErrorCode.PERMISSION_CURRENTLY_ASSIGNED));
    }

    @Test
    @DisplayName("Finding 5 - Test 3: Message contains constraint substring but structured constraint name is different -> rethrow without mapping")
    void testMessageContainsConstraintSubstringButStructuredNameDifferentRethrows() {
        CreatePermissionRequest createReq = CreatePermissionRequest.builder().code("PERM_CREATE_SUBSTR").build();
        when(permissionRepository.existsByCodeIgnoreCase("PERM_CREATE_SUBSTR")).thenReturn(false);

        // Message contains "uq_permissions_code_lower" but structured metadata says "chk_different_constraint"
        DataIntegrityViolationException deceptiveCreateEx = createHibernateDive(
                "duplicate key value violates unique constraint \"uq_permissions_code_lower\"",
                "chk_different_constraint"
        );
        doThrow(deceptiveCreateEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(createReq))
                .isSameAs(deceptiveCreateEx);

        // Message contains "fk_role_permissions_permission" on delete but structured metadata says "fk_other_table"
        UUID permId = UUID.randomUUID();
        Permission perm = new Permission(permId, "PERM_DELETE_SUBSTR", "desc");
        when(permissionRepository.findByIdForUpdate(permId)).thenReturn(Optional.of(perm));
        when(permissionRepository.countRoleAssignmentsByPermissionId(permId)).thenReturn(0L);

        DataIntegrityViolationException deceptiveDeleteEx = createHibernateDive(
                "violates foreign key constraint \"fk_role_permissions_permission\" on table \"role_permissions\"",
                "fk_other_table"
        );
        doThrow(deceptiveDeleteEx).when(permissionRepository).flush();

        assertThatThrownBy(() -> permissionService.deletePermission(permId))
                .isSameAs(deceptiveDeleteEx);
    }

    @Test
    @DisplayName("Finding 5 - Test 4: Constraint name is null -> rethrows DataIntegrityViolationException")
    void testConstraintNameNullRethrows() {
        CreatePermissionRequest createReq = CreatePermissionRequest.builder().code("PERM_NULL_CONSTRAINT").build();
        when(permissionRepository.existsByCodeIgnoreCase("PERM_NULL_CONSTRAINT")).thenReturn(false);

        // 4a. Constraint name is null in ConstraintViolationException
        DataIntegrityViolationException nullConstraintEx = createHibernateDive("error without constraint name", null);
        doThrow(nullConstraintEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(createReq))
                .isSameAs(nullConstraintEx);

        // 4b. No cause chain metadata at all
        DataIntegrityViolationException noCauseEx = new DataIntegrityViolationException("bare integrity error");
        doThrow(noCauseEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(createReq))
                .isSameAs(noCauseEx);
    }

    @Test
    @DisplayName("Finding 5 - Test 5: Unknown constraint name -> rethrows DataIntegrityViolationException")
    void testUnknownConstraintNameRethrows() {
        CreatePermissionRequest createReq = CreatePermissionRequest.builder().code("PERM_UNKNOWN_CONSTRAINT").build();
        when(permissionRepository.existsByCodeIgnoreCase("PERM_UNKNOWN_CONSTRAINT")).thenReturn(false);

        DataIntegrityViolationException unknownEx = createHibernateDive("unknown constraint error", "uq_some_other_table_field");
        doThrow(unknownEx).when(permissionRepository).saveAndFlush(any());

        assertThatThrownBy(() -> permissionService.createPermission(createReq))
                .isSameAs(unknownEx);
    }
}
