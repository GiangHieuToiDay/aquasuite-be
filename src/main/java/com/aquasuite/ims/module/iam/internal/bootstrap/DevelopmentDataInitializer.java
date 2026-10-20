package com.aquasuite.ims.module.iam.internal.bootstrap;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.authorization.repository.RoleRepository;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Component
@Slf4j(topic = "DATA INITIALIZER")
public class DevelopmentDataInitializer implements ApplicationRunner {

    public record RoleDefinition(String name, String description) {}

    public static final List<RoleDefinition> CANONICAL_ROLES = List.of(
            new RoleDefinition("SHOP_OWNER", "Chủ cửa hàng"),
            new RoleDefinition("SALES_STAFF", "Nhân viên bán hàng"),
            new RoleDefinition("ACCOUNTANT", "Kế toán"),
            new RoleDefinition("TECHNICAL_STAFF", "Nhân viên kỹ thuật"),
            new RoleDefinition("SHIPPER", "Nhân viên giao hàng"),
            new RoleDefinition("CUSTOMER", "Khách hàng")
    );

    public static final String OWNER_EMAIL = "owner@aquasuite.local";
    public static final String OWNER_USERNAME = "owner_test";

    public static class SeedConflictException extends IllegalStateException {
        public SeedConflictException(String message) {
            super(message);
        }
    }

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transactionTemplate;
    private final Environment environment;

    public DevelopmentDataInitializer(
            @org.springframework.beans.factory.annotation.Autowired(required = false) RoleRepository roleRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepository userRepository,
            @org.springframework.beans.factory.annotation.Autowired(required = false) PasswordEncoder passwordEncoder,
            @org.springframework.beans.factory.annotation.Autowired(required = false) TransactionTemplate transactionTemplate,
            Environment environment) {
        this.roleRepository = roleRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.transactionTemplate = transactionTemplate;
        this.environment = environment;
    }

    private Supplier<String> passwordSupplier = () -> System.getenv("AQUASUITE_DEV_OWNER_PASSWORD");

    void setPasswordSupplier(Supplier<String> passwordSupplier) {
        this.passwordSupplier = passwordSupplier;
    }

    @Override
    public void run(ApplicationArguments args) {
        initialize();
    }

    public boolean initialize() {
        if (!isEligibleForSeeding()) {
            return false;
        }

        if (roleRepository == null || userRepository == null || transactionTemplate == null || passwordEncoder == null) {
            log.info("Development data initializer skipped: JPA repositories or transaction manager not available.");
            return false;
        }

        String password = passwordSupplier.get();
        if (password == null || password.isBlank()) {
            throw new IllegalStateException("Development data seeding is enabled, but required environment variable AQUASUITE_DEV_OWNER_PASSWORD is null or blank.");
        }

        log.info("Starting development data seeding...");
        transactionTemplate.executeWithoutResult(status -> {
            Map<String, Role> managedRoles = createRoles();
            createOwner(password, managedRoles.get("SHOP_OWNER"));
        });
        log.info("Development data seeding completed successfully.");
        return true;
    }

    public boolean isEligibleForSeeding() {
        String enabledProp = environment.getProperty("aquasuite.iam.dev-seed.enabled");
        if (!Boolean.parseBoolean(enabledProp)) {
            log.info("Development data initializer is disabled (aquasuite.iam.dev-seed.enabled=false). Skipping.");
            return false;
        }

        String[] activeProfiles = environment.getActiveProfiles();
        Set<String> profiles = Arrays.stream(activeProfiles)
                .map(String::toLowerCase)
                .collect(Collectors.toSet());

        if (profiles.contains("prod")) {
            log.warn("Development data initializer blocked: 'prod' profile is active.");
            return false;
        }

        if (!profiles.contains("dev")) {
            log.info("Development data initializer blocked: active profiles do not contain 'dev'. Active profiles: {}", Arrays.toString(activeProfiles));
            return false;
        }

        String envProp = environment.getProperty("aquasuite.config.environment");
        if (!"dev".equalsIgnoreCase(envProp)) {
            log.warn("Development data initializer blocked: aquasuite.config.environment is '{}', expected 'dev'.", envProp);
            return false;
        }

        return true;
    }

    private Map<String, Role> createRoles() {
        Map<String, Role> roleMap = new HashMap<>();

        for (RoleDefinition def : CANONICAL_ROLES) {
            Optional<Role> existingOpt = roleRepository.findByRoleNameIgnoreCase(def.name());
            if (existingOpt.isPresent()) {
                Role existing = existingOpt.get();
                if (!def.name().equals(existing.getRoleName())) {
                    throw new SeedConflictException("Role name case mismatch for " + def.name() + ": found " + existing.getRoleName());
                }
                if (!existing.isActive()) {
                    throw new SeedConflictException("Role " + def.name() + " exists in database but is inactive.");
                }
                roleMap.put(def.name(), existing);
            } else {
                Role role = new Role();
                role.setRoleId(UUID.randomUUID());
                role.setRoleName(def.name());
                role.setDescription(def.description());
                role.setActive(true);
                role.setPermissions(new HashSet<>());
                Role saved = roleRepository.save(role);
                roleMap.put(def.name(), saved);
                log.info("Created missing canonical role: {}", def.name());
            }
        }

        return roleMap;
    }

    private void createOwner(String password, Role shopOwnerRole) {
        if (shopOwnerRole == null) {
            throw new IllegalStateException("SHOP_OWNER role is required before seeding owner user.");
        }

        Optional<User> byEmail = userRepository.findByEmailIgnoreCase(OWNER_EMAIL);
        Optional<User> byUsername = userRepository.findByUsernameIgnoreCase(OWNER_USERNAME);

        if (byEmail.isPresent() && byUsername.isPresent()) {
            User userEmail = byEmail.get();
            User userUsername = byUsername.get();

            if (!userEmail.getId().equals(userUsername.getId())) {
                throw new SeedConflictException("Owner identity collision: email and username belong to different users.");
            }

            User user = userEmail;
            if (!OWNER_EMAIL.equals(user.getEmail())) {
                throw new SeedConflictException("Owner email case mismatch: expected " + OWNER_EMAIL + ", found " + user.getEmail());
            }

            if (!OWNER_USERNAME.equals(user.getUsername())) {
                throw new SeedConflictException("Owner username case mismatch: expected " + OWNER_USERNAME + ", found " + user.getUsername());
            }

            if (user.getStatus() != UserStatus.ACTIVE) {
                throw new SeedConflictException("Owner account exists with non-ACTIVE status: " + user.getStatus());
            }

            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new SeedConflictException("Owner account exists but password hash does not match AQUASUITE_DEV_OWNER_PASSWORD.");
            }

            Set<Role> roles = user.getRoles();
            if (roles == null || roles.size() != 1 || !roles.stream().allMatch(r -> "SHOP_OWNER".equals(r.getRoleName()))) {
                String roleNames = roles == null ? "[]" : roles.stream().map(Role::getRoleName).collect(Collectors.toSet()).toString();
                throw new SeedConflictException("Owner account exists but role set mismatch: expected [SHOP_OWNER], found " + roleNames);
            }

            log.info("Development owner user 'owner_test' already exists with matching state. Retaining existing record and hash.");
        } else if (byEmail.isPresent() || byUsername.isPresent()) {
            throw new SeedConflictException("Owner identity partial collision: either email or username is already taken by another user.");
        } else {
            User user = new User();
            user.setId(UUID.randomUUID());
            user.setEmail(OWNER_EMAIL);
            user.setUsername(OWNER_USERNAME);
            user.setStatus(UserStatus.ACTIVE);
            user.setPasswordHash(passwordEncoder.encode(password));
            user.setRoles(new HashSet<>(Set.of(shopOwnerRole)));

            userRepository.save(user);
            log.info("Created development owner user: {} / {}", OWNER_EMAIL, OWNER_USERNAME);
        }
    }
}
