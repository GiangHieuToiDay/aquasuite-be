package com.aquasuite.ims.module.iam.internal.repository;


import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesAndPermissionsById(UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesAndPermissionsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, UUID id);

    boolean existsByEmailIgnoreCase(String email);
///==========================================================================================
    boolean existsByEmailIgnoreCaseAndIdNot(String email, UUID id);

    boolean existsByPhone(String phone);

    boolean existsByPhoneAndIdNot(String phone, UUID id);

    @Query("""
        select distinct u
        from User u
        left join u.roles r
        where (
            :keyword = ''
            or lower(u.username) like concat('%', :keyword, '%')
            or lower(u.email) like concat('%', :keyword, '%')
            or u.phone like concat('%', :keyword, '%')
        )
        and (:roleId is null or r.roleId = :roleId)
        and (:status is null or u.status = :status)
        """)
    Page<User> search(
            @Param("keyword") String keyword,
            @Param("roleId") UUID roleId,
            @Param("status") UserStatus status,
            Pageable pageable
    );

    long countByStatus(UserStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select distinct u.id from User u join u.roles r where u.status = 'ACTIVE' and upper(r.roleName) in ('STORE_OWNER', 'ROLE_STORE_OWNER')")
    List<UUID> lockActiveStoreOwnerIds();

    @Modifying(flushAutomatically = true)
    @Query(value = "update public.user_roles set assigned_by = :actorId where user_id = :userId", nativeQuery = true)
    void markRolesAssignedBy(@Param("userId") UUID userId, @Param("actorId") UUID actorId);
    /** Avoids logging a credential-bearing failed INSERT during duplicate registration races. */
    @Modifying
    @Query(
            value =
                    """
            INSERT INTO public.users(id, username, full_name, email, phone, password_hash, status, provider, is_verified)
            VALUES (:id, :username, :fullName, :email, :phone, :passwordHash, 'PENDING_ACTIVATION', 'LOCAL', false)
            ON CONFLICT DO NOTHING
            """,
            nativeQuery = true)
    int insertPendingRegistration(
            @Param("id") UUID id,
            @Param("username") String username,
            @Param("fullName") String fullName,
            @Param("email") String email,
            @Param("phone") String phone,
            @Param("passwordHash") String passwordHash);

    @Lock(
            LockModeType.PESSIMISTIC_WRITE)
    @Query(
            "select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findForRegistrationByEmail(
            @Param("email") String email);
}
