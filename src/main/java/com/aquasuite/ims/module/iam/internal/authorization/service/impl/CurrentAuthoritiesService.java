package com.aquasuite.ims.module.iam.internal.authorization.service.impl;

import com.aquasuite.ims.module.iam.internal.authorization.entity.Permission;
import com.aquasuite.ims.module.iam.internal.authorization.entity.Role;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CurrentAuthoritiesService {

    private static final Pattern ROLE_SUFFIX_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;

    public CurrentAuthoritiesService(
            @org.springframework.beans.factory.annotation.Autowired(required = false)
                    UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public Optional<User> loadCurrentUser(UUID userId) {
        if (userRepository == null) {
            return Optional.empty();
        }
        return userRepository.findWithRolesAndPermissionsById(userId);
    }

    /**
     * Resolves Spring Security GrantedAuthorities from user's current roles in PostgreSQL.
     * Enforces:
     * 1. Inactive roles are excluded completely (no role authority, no permissions).
     * 2. Role normalization: trim, uppercase with Locale.ROOT, exactly one ROLE_ prefix.
     * 3. Role name valid suffix: ^[A-Z0-9_]+$. Invalid role names excluded completely.
     * 4. Role collision: if two different role IDs map to the same normalized role authority,
     *    neither role authority nor permissions from colliding roles are granted.
     * 5. Permission code: verbatim code preserved. Null/blank excluded.
     *    If code.trim().toUpperCase(Locale.ROOT).startsWith("ROLE_"), belongs to role namespace
     *    and is strictly prohibited from being granted as any GrantedAuthority.
     */
    public Collection<GrantedAuthority> resolveAuthorities(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        // Map normalized role authority -> list of Role
        Map<String, List<Role>> normalizedMap = new HashMap<>();

        for (Role role : roles) {
            if (role == null || !role.isActive()) {
                continue;
            }

            String roleName = role.getRoleName();
            if (roleName == null || roleName.trim().isEmpty()) {
                log.warn(
                        "Encountered role with null or blank roleName for roleId: {}",
                        role.getRoleId());
                continue;
            }

            String trimmed = roleName.trim().toUpperCase(Locale.ROOT);
            String suffix =
                    trimmed.startsWith(ROLE_PREFIX)
                            ? trimmed.substring(ROLE_PREFIX.length())
                            : trimmed;

            if (suffix.isEmpty() || !ROLE_SUFFIX_PATTERN.matcher(suffix).matches()) {
                log.warn(
                        "Invalid role name '{}' for roleId: {}, skipping",
                        roleName,
                        role.getRoleId());
                continue;
            }

            String normalizedAuth = ROLE_PREFIX + suffix;
            normalizedMap.computeIfAbsent(normalizedAuth, k -> new ArrayList<>()).add(role);
        }

        Set<GrantedAuthority> authorities = new HashSet<>();

        for (Map.Entry<String, List<Role>> entry : normalizedMap.entrySet()) {
            String normalizedAuth = entry.getKey();
            List<Role> roleList = entry.getValue();

            // Collision detection: multiple distinct role IDs mapping to same normalized authority
            Set<UUID> uniqueRoleIds = new HashSet<>();
            for (Role r : roleList) {
                uniqueRoleIds.add(r.getRoleId());
            }

            if (uniqueRoleIds.size() > 1) {
                log.warn(
                        "Role authority collision detected for '{}' across role IDs: {}. Disqualifying colliding roles.",
                        normalizedAuth,
                        uniqueRoleIds);
                // Do not grant role authority and do not grant permissions from colliding roles
                continue;
            }

            // Valid non-colliding active role
            authorities.add(new SimpleGrantedAuthority(normalizedAuth));

            Role validRole = roleList.get(0);
            Set<Permission> permissions = validRole.getPermissions();
            if (permissions != null) {
                for (Permission permission : permissions) {
                    if (permission == null) {
                        continue;
                    }
                    String code = permission.getCode();
                    if (code == null || code.trim().isEmpty()) {
                        continue;
                    }

                    // Namespace rule: permission code starting with ROLE_ (case-insensitive) is
                    // forbidden
                    if (code.trim().toUpperCase(Locale.ROOT).startsWith(ROLE_PREFIX)) {
                        log.warn(
                                "Permission code '{}' uses forbidden ROLE_ namespace. Disqualifying from authority grant.",
                                code);
                        continue;
                    }

                    // Verbatim permission code preserved
                    authorities.add(new SimpleGrantedAuthority(code));
                }
            }
        }

        return authorities;
    }

    /**
     * Resolves clean active role names (without ROLE_ prefix) for login response user.roles.
     * Follows same collision and validity rules.
     */
    public List<String> resolveActiveRoleNames(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, List<Role>> normalizedMap = new HashMap<>();

        for (Role role : roles) {
            if (role == null || !role.isActive()) {
                continue;
            }

            String roleName = role.getRoleName();
            if (roleName == null || roleName.trim().isEmpty()) {
                continue;
            }

            String trimmed = roleName.trim().toUpperCase(Locale.ROOT);
            String suffix =
                    trimmed.startsWith(ROLE_PREFIX)
                            ? trimmed.substring(ROLE_PREFIX.length())
                            : trimmed;

            if (suffix.isEmpty() || !ROLE_SUFFIX_PATTERN.matcher(suffix).matches()) {
                continue;
            }

            normalizedMap.computeIfAbsent(suffix, k -> new ArrayList<>()).add(role);
        }

        List<String> result = new ArrayList<>();
        for (Map.Entry<String, List<Role>> entry : normalizedMap.entrySet()) {
            String cleanName = entry.getKey();
            List<Role> roleList = entry.getValue();

            Set<UUID> uniqueRoleIds = new HashSet<>();
            for (Role r : roleList) {
                uniqueRoleIds.add(r.getRoleId());
            }

            if (uniqueRoleIds.size() > 1) {
                // Collision -> disqualified
                continue;
            }

            result.add(cleanName);
        }

        Collections.sort(result);
        return result;
    }
}
