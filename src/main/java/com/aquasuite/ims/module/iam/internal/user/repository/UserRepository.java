package com.aquasuite.ims.module.iam.internal.user.repository;

import com.aquasuite.ims.module.iam.internal.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {

    Optional<User> findByPhone(String phone);

    Optional<User> findByUsernameIgnoreCase(String username);

    Optional<User> findByEmailIgnoreCase(String email);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesAndPermissionsById(UUID id);

    @EntityGraph(attributePaths = {"roles", "roles.permissions"})
    Optional<User> findWithRolesAndPermissionsByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByPhone(String phone);

    /** Avoids logging a credential-bearing failed INSERT during duplicate registration races. */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query(value = """
            INSERT INTO public.users(id, username, full_name, email, phone, password_hash, status)
            VALUES (:id, :username, :fullName, :email, :phone, :passwordHash, 'PENDING_ACTIVATION')
            ON CONFLICT DO NOTHING
            """, nativeQuery = true)
    int insertPendingRegistration(
            @org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("username") String username,
            @org.springframework.data.repository.query.Param("fullName") String fullName,
            @org.springframework.data.repository.query.Param("email") String email,
            @org.springframework.data.repository.query.Param("phone") String phone,
            @org.springframework.data.repository.query.Param("passwordHash") String passwordHash);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select u from User u where lower(u.email) = lower(:email)")
    Optional<User> findForRegistrationByEmail(@org.springframework.data.repository.query.Param("email") String email);
}
