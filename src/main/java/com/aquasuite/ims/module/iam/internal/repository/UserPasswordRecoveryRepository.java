package com.aquasuite.ims.module.iam.internal.repository;
import com.aquasuite.ims.module.iam.internal.entity.UserPasswordRecovery;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface UserPasswordRecoveryRepository extends JpaRepository<UserPasswordRecovery, UUID> {}
