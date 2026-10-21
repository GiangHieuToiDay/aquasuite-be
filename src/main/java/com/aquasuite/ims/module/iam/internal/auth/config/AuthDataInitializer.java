package com.aquasuite.ims.module.iam.internal.auth.config;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.authorization.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.AuthProvider;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class AuthDataInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(String... args) {
        initRoles();
        initDefaultUsers();
    }

    private void initRoles() {
        if (!roleRepository.existsByRoleNameIgnoreCase("CUSTOMER")) {
            Role customerRole = new Role();
            customerRole.setRoleId(UUID.randomUUID());
            customerRole.setRoleName("CUSTOMER");
            customerRole.setDescription("Khách hàng cá nhân / thương mại");
            customerRole.setActive(true);
            roleRepository.save(customerRole);
            log.info("Initialized default role: CUSTOMER");
        }

        if (!roleRepository.existsByRoleNameIgnoreCase("ADMIN")) {
            Role adminRole = new Role();
            adminRole.setRoleId(UUID.randomUUID());
            adminRole.setRoleName("ADMIN");
            adminRole.setDescription("Quản trị viên hệ thống");
            adminRole.setActive(true);
            roleRepository.save(adminRole);
            log.info("Initialized default role: ADMIN");
        }

        if (!roleRepository.existsByRoleNameIgnoreCase("STAFF")) {
            Role staffRole = new Role();
            staffRole.setRoleId(UUID.randomUUID());
            staffRole.setRoleName("STAFF");
            staffRole.setDescription("Nhân viên nghiệp vụ");
            staffRole.setActive(true);
            roleRepository.save(staffRole);
            log.info("Initialized default role: STAFF");
        }
    }

    private void initDefaultUsers() {
        Role customerRole = roleRepository.findByRoleNameIgnoreCase("CUSTOMER").orElse(null);
        Role adminRole = roleRepository.findByRoleNameIgnoreCase("ADMIN").orElse(null);

        // 1. Seed demo customer user: customer@aquasuite.com / Password@123
        if (userRepository.findByEmailIgnoreCase("customer@aquasuite.com").isEmpty()) {
            String username = "demo_customer";
            if (userRepository.existsByUsernameIgnoreCase(username)) {
                username = "demo_customer_" + UUID.randomUUID().toString().substring(0, 6);
            }
            User customerUser = new User();
            customerUser.setId(UUID.fromString("a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11"));
            customerUser.setUsername(username);
            customerUser.setFullName("Nguyễn Văn A");
            customerUser.setEmail("customer@aquasuite.com");
            customerUser.setPhone("0987654321");
            customerUser.setPasswordHash(passwordEncoder.encode("Password@123"));
            customerUser.setStatus(UserStatus.ACTIVE);
            customerUser.setVerified(true);
            customerUser.setProvider(AuthProvider.LOCAL);
            customerUser.setCreatedAt(Instant.now());
            if (customerRole != null) {
                customerUser.setRoles(new HashSet<>(Set.of(customerRole)));
            }
            userRepository.save(customerUser);
            log.info("Initialized default user: customer@aquasuite.com (Password@123)");
        }

        // 2. Seed demo admin user: admin@aquasuite.com / Password@123
        if (userRepository.findByEmailIgnoreCase("admin@aquasuite.com").isEmpty()) {
            String adminUsername = "admin";
            if (userRepository.existsByUsernameIgnoreCase(adminUsername)) {
                adminUsername = "admin_" + UUID.randomUUID().toString().substring(0, 6);
            }
            User adminUser = new User();
            adminUser.setId(UUID.randomUUID());
            adminUser.setUsername(adminUsername);
            adminUser.setFullName("Quản Trị Viên");
            adminUser.setEmail("admin@aquasuite.com");
            adminUser.setPhone("0911223344");
            adminUser.setPasswordHash(passwordEncoder.encode("Password@123"));
            adminUser.setStatus(UserStatus.ACTIVE);
            adminUser.setVerified(true);
            adminUser.setProvider(AuthProvider.LOCAL);
            adminUser.setCreatedAt(Instant.now());
            if (adminRole != null) {
                adminUser.setRoles(new HashSet<>(Set.of(adminRole)));
            }
            userRepository.save(adminUser);
            log.info("Initialized default user: admin@aquasuite.com (Password@123)");
        }
    }
}
