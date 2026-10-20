package com.aquasuite.ims.module.iam.internal.service;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.error.IamErrorCode;
import com.aquasuite.ims.module.iam.internal.payload.request.CreatePermissionRequest;
import com.aquasuite.ims.module.iam.internal.repository.PermissionRepository;
import com.aquasuite.ims.module.iam.internal.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class IamConcurrencyAndRepositoryIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private IamPermissionService permissionService;

    @Autowired
    private IamRoleService roleService;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private PermissionRepository permissionRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private DataSource dataSource;

    @AfterEach
    void cleanUp() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("DELETE FROM public.role_permissions");
            stmt.executeUpdate("DELETE FROM public.user_roles");
            stmt.executeUpdate("DELETE FROM public.permissions");
            stmt.executeUpdate("DELETE FROM public.roles WHERE role_name NOT IN ('SHOP_OWNER', 'SALES_STAFF', 'ACCOUNTANT', 'TECHNICAL_STAFF', 'SHIPPER', 'CUSTOMER')");
            stmt.executeUpdate("DELETE FROM public.users");
        }
    }

    @Test
    @DisplayName("AC-06, AC-21: Concurrent permission create with identical case-insensitive code: 1 succeeds, other receives IAM_005")
    void testConcurrentDuplicateCreate() throws Exception {
        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startLatch.await();
                    // Alternate cases
                    String code = (index % 2 == 0) ? "INVENTORY_AUDIT" : "inventory_audit";
                    CreatePermissionRequest req = CreatePermissionRequest.builder()
                            .code(code)
                            .description("Concurrent audit perm")
                            .build();
                    permissionService.createPermission(req);
                    successCount.incrementAndGet();
                } catch (AppException ex) {
                    if (ex.getErrorCode() == IamErrorCode.PERMISSION_CODE_DUPLICATE) {
                        conflictCount.incrementAndGet();
                    } else {
                        errors.add(ex);
                    }
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threads - 1);

        // Exactly 1 permission in DB
        List<Permission> perms = permissionRepository.findAll();
        assertThat(perms).hasSize(1);
        assertThat(perms.get(0).getCode()).isEqualTo("INVENTORY_AUDIT");
    }

    @Test
    @DisplayName("AC-09, AC-22: Concurrent permission assign to same role: exactly 1 mapping created, no duplicates")
    void testConcurrentDuplicateAssign() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "WAREHOUSE_READ", "Read warehouse");
        permissionRepository.saveAndFlush(p);

        Role role = roleRepository.findByRoleNameIgnoreCase("TECHNICAL_STAFF").orElseThrow();

        int threads = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threads);

        AtomicInteger successCount = new AtomicInteger(0);
        List<Throwable> errors = Collections.synchronizedList(new ArrayList<>());

        for (int i = 0; i < threads; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    roleService.assignPermissionToRole(role.getRoleId(), p.getId());
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        boolean completed = doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        assertThat(errors).isEmpty();
        assertThat(successCount.get()).isEqualTo(threads);

        // Verify exactly 1 mapping in role_permissions table
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM public.role_permissions WHERE role_id = ? AND permission_id = ?")) {
            ps.setObject(1, role.getRoleId());
            ps.setObject(2, p.getId());
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(1);
            }
        }
    }

    @Test
    @DisplayName("AC-18, AC-23, AC-33: Delete-versus-assign race: deterministic IAM_006 or IAM_004, no dangling references")
    void testDeleteVersusAssignRace() throws Exception {
        Permission p = new Permission(UUID.randomUUID(), "RACE_PERM", "Race permission");
        permissionRepository.saveAndFlush(p);

        Role role = roleRepository.findByRoleNameIgnoreCase("SHIPPER").orElseThrow();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch startLatch = new CountDownLatch(1);

        Future<String> assignTask = executor.submit(() -> {
            startLatch.await();
            try {
                roleService.assignPermissionToRole(role.getRoleId(), p.getId());
                return "ASSIGN_SUCCESS";
            } catch (AppException ex) {
                return ex.getErrorCode().getCode();
            }
        });

        Future<String> deleteTask = executor.submit(() -> {
            startLatch.await();
            try {
                permissionService.deletePermission(p.getId());
                return "DELETE_SUCCESS";
            } catch (AppException ex) {
                return ex.getErrorCode().getCode();
            }
        });

        startLatch.countDown();

        String assignResult = assignTask.get(10, TimeUnit.SECONDS);
        String deleteResult = deleteTask.get(10, TimeUnit.SECONDS);
        executor.shutdown();

        // Either Assign won (Assign success, Delete IAM_006)
        // OR Delete won (Delete success, Assign IAM_004)
        if ("ASSIGN_SUCCESS".equals(assignResult)) {
            assertThat(deleteResult).isEqualTo("IAM_006");
            assertThat(permissionRepository.findById(p.getId())).isPresent();
        } else {
            assertThat(deleteResult).isEqualTo("DELETE_SUCCESS");
            assertThat(assignResult).isEqualTo("IAM_004");
            assertThat(permissionRepository.findById(p.getId())).isEmpty();
        }

        // Verify no dangling reference in role_permissions
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT COUNT(*) FROM public.role_permissions rp LEFT JOIN public.permissions p ON rp.permission_id = p.id WHERE p.id IS NULL")) {
            try (ResultSet rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getLong(1)).isEqualTo(0);
            }
        }
    }

    @Test
    @DisplayName("AC-02, AC-24: UC mutations never alter user_roles table")
    void testUserRolesNeverModifiedByUcMutations() throws Exception {
        Role customerRole = roleRepository.findByRoleNameIgnoreCase("CUSTOMER").orElseThrow();

        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("cust_user");
        user.setEmail("cust@aquasuite.local");
        user.setPasswordHash("dummy_hash");
        user.setStatus(UserStatus.ACTIVE);
        user.setRoles(new HashSet<>(Set.of(customerRole)));
        userRepository.save(user);

        long initialUserRolesCount;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.user_roles")) {
            rs.next();
            initialUserRolesCount = rs.getLong(1);
        }
        assertThat(initialUserRolesCount).isEqualTo(1);

        // Perform permission creation
        CreatePermissionRequest createReq = CreatePermissionRequest.builder()
                .code("NEW_PERM_USER_CHECK")
                .description("Check user_roles invariant")
                .build();
        var created = permissionService.createPermission(createReq);

        // Assign to role
        roleService.assignPermissionToRole(customerRole.getRoleId(), created.getId());

        // Unassign from role
        roleService.unassignPermissionFromRole(customerRole.getRoleId(), created.getId());

        // Delete permission
        permissionService.deletePermission(created.getId());

        // Verify user_roles count is still exactly initialUserRolesCount
        long finalUserRolesCount;
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM public.user_roles")) {
            rs.next();
            finalUserRolesCount = rs.getLong(1);
        }
        assertThat(finalUserRolesCount).isEqualTo(initialUserRolesCount);
    }
}
