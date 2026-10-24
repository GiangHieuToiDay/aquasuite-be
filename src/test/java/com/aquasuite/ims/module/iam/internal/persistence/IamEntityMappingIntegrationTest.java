package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.module.iam.internal.entity.Permission;
import com.aquasuite.ims.module.iam.internal.entity.Role;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.testsupport.PostgreSqlIntegrationTestSupport;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class IamEntityMappingIntegrationTest extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

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
    @DisplayName("AC-06: Target architecture package structure and encapsulation")
    void testPackageEncapsulation() {
        assertThat(User.class.getPackageName()).isEqualTo("com.aquasuite.ims.module.iam.internal.entity");
        assertThat(Role.class.getPackageName()).isEqualTo("com.aquasuite.ims.module.iam.internal.entity");
        assertThat(Permission.class.getPackageName()).isEqualTo("com.aquasuite.ims.module.iam.internal.entity");
        assertThat(UserStatus.class.getPackageName()).isEqualTo("com.aquasuite.ims.module.iam.internal.entity");
    }

    @Test
    @DisplayName("AC-07: Exactly two ManyToMany LAZY associations, empty cascade, exact JoinTable metadata, no join entity or embedded IDs")
    void testAssociationCountAndLazyNoCascade() throws NoSuchFieldException {
        List<Class<?>> entities = List.of(User.class, Role.class, Permission.class);
        int totalManyToManyCount = 0;
        for (Class<?> entity : entities) {
            for (Field f : entity.getDeclaredFields()) {
                if (f.isAnnotationPresent(ManyToMany.class)) {
                    totalManyToManyCount++;
                }
                assertThat(f.isAnnotationPresent(ManyToOne.class)).isFalse();
                assertThat(f.isAnnotationPresent(OneToMany.class)).isFalse();
                assertThat(f.isAnnotationPresent(OneToOne.class)).isFalse();
                assertThat(f.isAnnotationPresent(EmbeddedId.class)).isFalse();
            }
        }
        assertThat(totalManyToManyCount).isEqualTo(2);

        // Inspect User.roles
        Field rolesField = User.class.getDeclaredField("roles");
        ManyToMany userRolesM2M = rolesField.getAnnotation(ManyToMany.class);
        assertThat(userRolesM2M).isNotNull();
        assertThat(userRolesM2M.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(userRolesM2M.cascade()).isEmpty();

        JoinTable userRolesJoin = rolesField.getAnnotation(JoinTable.class);
        assertThat(userRolesJoin).isNotNull();
        assertThat(userRolesJoin.name()).isEqualTo("user_roles");
        assertThat(userRolesJoin.schema()).isEqualTo("public");
        assertThat(userRolesJoin.joinColumns()).hasSize(1);
        assertThat(userRolesJoin.joinColumns()[0].name()).isEqualTo("user_id");
        assertThat(userRolesJoin.inverseJoinColumns()).hasSize(1);
        assertThat(userRolesJoin.inverseJoinColumns()[0].name()).isEqualTo("role_id");

        // Inspect Role.permissions
        Field permsField = Role.class.getDeclaredField("permissions");
        ManyToMany rolePermsM2M = permsField.getAnnotation(ManyToMany.class);
        assertThat(rolePermsM2M).isNotNull();
        assertThat(rolePermsM2M.fetch()).isEqualTo(FetchType.LAZY);
        assertThat(rolePermsM2M.cascade()).isEmpty();

        JoinTable rolePermsJoin = permsField.getAnnotation(JoinTable.class);
        assertThat(rolePermsJoin).isNotNull();
        assertThat(rolePermsJoin.name()).isEqualTo("role_permissions");
        assertThat(rolePermsJoin.schema()).isEqualTo("public");
        assertThat(rolePermsJoin.joinColumns()).hasSize(1);
        assertThat(rolePermsJoin.joinColumns()[0].name()).isEqualTo("role_id");
        assertThat(rolePermsJoin.inverseJoinColumns()).hasSize(1);
        assertThat(rolePermsJoin.inverseJoinColumns()[0].name()).isEqualTo("permission_id");
    }

    @Test
    @DisplayName("AC-02: Application generates UUID prior to persist when null and preserves assigned UUID")
    void testApplicationGeneratedUuidPrePersist() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        User user1 = new User();
        user1.setUsername("uuid_user1");
        user1.setEmail("uuid_user1@test.com");
        user1.setPasswordHash("hash");
        tx.execute(status -> {
            entityManager.persist(user1);
            entityManager.flush();
            return null;
        });
        assertThat(user1.getId()).isNotNull();

        UUID presetId = UUID.randomUUID();
        User user2 = new User();
        user2.setId(presetId);
        user2.setUsername("uuid_user2");
        user2.setEmail("uuid_user2@test.com");
        user2.setPasswordHash("hash");
        tx.execute(status -> {
            entityManager.persist(user2);
            entityManager.flush();
            return null;
        });
        assertThat(user2.getId()).isEqualTo(presetId);
    }

    @Test
    @DisplayName("AC-16: Database generates created_at on persist for both users and roles, reloaded into Instant, immutable on update")
    void testDatabaseGeneratedCreatedAtAndImmutable() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Verify User created_at
        UUID userId = tx.execute(status -> {
            User user = new User();
            user.setUsername("timestamp_user");
            user.setEmail("timestamp_user@test.com");
            user.setPasswordHash("hash");
            user.setPhone("0900000001");
            entityManager.persist(user);
            entityManager.flush();
            return user.getId();
        });

        Instant initialUserCreatedAt = tx.execute(status -> {
            User reloaded = entityManager.find(User.class, userId);
            assertThat(reloaded).isNotNull();
            assertThat(reloaded.getCreatedAt()).isNotNull();
            reloaded.setPhone("0900000002");
            entityManager.merge(reloaded);
            entityManager.flush();
            return reloaded.getCreatedAt();
        });

        tx.execute(status -> {
            User reloadedAgain = entityManager.find(User.class, userId);
            assertThat(reloadedAgain).isNotNull();
            assertThat(reloadedAgain.getPhone()).isEqualTo("0900000002");
            assertThat(reloadedAgain.getCreatedAt()).isEqualTo(initialUserCreatedAt);
            return null;
        });

        // 2. Verify Role created_at (Spec AC-16)
        UUID roleId = tx.execute(status -> {
            Role role = new Role();
            role.setRoleName("TIMESTAMP_ROLE");
            role.setDescription("Initial role description");
            entityManager.persist(role);
            entityManager.flush();
            return role.getRoleId();
        });

        Instant initialRoleCreatedAt = tx.execute(status -> {
            Role reloadedRole = entityManager.find(Role.class, roleId);
            assertThat(reloadedRole).isNotNull();
            assertThat(reloadedRole.getCreatedAt()).isNotNull();
            reloadedRole.setDescription("Updated role description");
            entityManager.merge(reloadedRole);
            entityManager.flush();
            return reloadedRole.getCreatedAt();
        });

        tx.execute(status -> {
            Role reloadedRoleAgain = entityManager.find(Role.class, roleId);
            assertThat(reloadedRoleAgain).isNotNull();
            assertThat(reloadedRoleAgain.getDescription()).isEqualTo("Updated role description");
            assertThat(reloadedRoleAgain.getCreatedAt()).isEqualTo(initialRoleCreatedAt);
            return null;
        });
    }

    @Test
    @DisplayName("AC-17: JPA UserStatus enum string mapping round-trips all four values")
    void testUserStatusJpaEnumStringRoundTrip() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (UserStatus statusVal : UserStatus.values()) {
            UUID id = tx.execute(status -> {
                User user = new User();
                user.setUsername("status_" + statusVal.name().toLowerCase());
                user.setEmail("status_" + statusVal.name().toLowerCase() + "@test.com");
                user.setPasswordHash("hash");
                user.setStatus(statusVal);
                entityManager.persist(user);
                entityManager.flush();
                return user.getId();
            });

            tx.execute(status -> {
                User reloaded = entityManager.find(User.class, id);
                assertThat(reloaded.getStatus()).isEqualTo(statusVal);

                String rawStatus = (String) entityManager.createNativeQuery(
                        "SELECT status FROM public.users WHERE id = :id")
                        .setParameter("id", id)
                        .getSingleResult();
                assertThat(rawStatus).isEqualTo(statusVal.name());
                return null;
            });
        }
    }

    @Test
    @DisplayName("AC-07: ManyToMany association persistence, lazy traversal, join row removal, and restrict delete")
    void testManyToManyLifecycleAndRestrictDelete() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        // 1. Create role and user, link role to user
        UUID[] ids = tx.execute(status -> {
            Role role = new Role();
            role.setRoleName("ROLE_MANAGER");
            entityManager.persist(role);

            Permission perm = new Permission();
            perm.setCode("MANAGE_USERS");
            entityManager.persist(perm);

            role.getPermissions().add(perm);
            entityManager.flush();

            User user = new User();
            user.setUsername("m2m_user");
            user.setEmail("m2m@test.com");
            user.setPasswordHash("hash");
            user.getRoles().add(role);
            entityManager.persist(user);
            entityManager.flush();

            return new UUID[]{user.getId(), role.getRoleId(), perm.getId()};
        });

        UUID userId = ids[0];
        UUID roleId = ids[1];
        UUID permId = ids[2];

        // 2. Traversal within transaction
        tx.execute(status -> {
            User reloadedUser = entityManager.find(User.class, userId);
            assertThat(reloadedUser.getRoles()).hasSize(1);
            Role linkedRole = reloadedUser.getRoles().iterator().next();
            assertThat(linkedRole.getRoleId()).isEqualTo(roleId);
            assertThat(linkedRole.getPermissions()).hasSize(1);
            assertThat(linkedRole.getPermissions().iterator().next().getId()).isEqualTo(permId);
            return null;
        });

        // 3. Deleting referenced target (role) while join row exists is rejected by foreign key RESTRICT
        assertThatThrownBy(() -> tx.execute(status -> {
            Role roleToDelete = entityManager.find(Role.class, roleId);
            entityManager.remove(roleToDelete);
            entityManager.flush();
            return null;
        })).isInstanceOf(Exception.class);

        // 4. Removing role from user.getRoles() deletes only join row in user_roles, leaves user and role intact
        tx.execute(status -> {
            User user = entityManager.find(User.class, userId);
            user.getRoles().clear();
            entityManager.flush();
            return null;
        });

        tx.execute(status -> {
            User user = entityManager.find(User.class, userId);
            assertThat(user.getRoles()).isEmpty();
            Role role = entityManager.find(Role.class, roleId);
            assertThat(role).isNotNull(); // Role is still preserved
            return null;
        });
    }
}
