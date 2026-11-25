package com.aquasuite.ims.service.iam.impl;

import com.aquasuite.ims.entity.iam.Permission;
import com.aquasuite.ims.entity.iam.Role;
import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.repository.iam.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

/**
 * Tải người dùng cùng vai trò, quyền hiện hành từ PostgreSQL và chuyển chúng thành danh sách quyền
 * của Spring Security. Dùng chung cho đăng nhập và cho mỗi request có token, nên mọi quy tắc chuẩn
 * hóa vai trò và lọc quyền nằm tập trung ở đây.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CurrentAuthoritiesService {

    /** Phần hậu tố tên vai trò hợp lệ: chỉ chữ in hoa, số và dấu gạch dưới. */
    private static final Pattern ROLE_SUFFIX_PATTERN = Pattern.compile("^[A-Z0-9_]+$");
    /** Tiền tố chuẩn của quyền dạng vai trò trong Spring Security. */
    private static final String ROLE_PREFIX = "ROLE_";

    private final UserRepository userRepository;

    /** Tải người dùng cùng vai trò và quyền hiện hành trong một truy vấn đọc. */
    @Transactional(readOnly = true)
    public Optional<User> loadCurrentUser(UUID userId) {
        return userRepository.findWithRolesAndPermissionsById(userId);
    }

    /**
     * Tính danh sách GrantedAuthority từ các vai trò hiện hành của người dùng. Quy tắc vai trò dùng chung
     * với {@link #resolveActiveRoleNames} (xem {@link #resolveValidRoles}). Mã permission được giữ nguyên
     * như trong cơ sở dữ liệu; mã rỗng bị bỏ; mã bắt đầu bằng "ROLE_" (không phân biệt hoa thường) thuộc
     * không gian tên vai trò nên tuyệt đối không được cấp thành quyền.
     */
    public Collection<GrantedAuthority> resolveAuthorities(Set<Role> roles) {
        Set<GrantedAuthority> authorities = new HashSet<>();

        resolveValidRoles(roles).forEach((suffix, role) -> {
            // Mỗi vai trò hợp lệ cấp một quyền dạng ROLE_<TÊN>
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + suffix));

            if (role.getPermissions() == null) {
                return;
            }
            for (Permission permission : role.getPermissions()) {
                String code = permission == null ? null : permission.getCode();
                if (code == null || code.trim().isEmpty()) {
                    continue;
                }
                // Permission mượn không gian tên ROLE_ bị loại để không giả mạo vai trò
                if (code.trim().toUpperCase(Locale.ROOT).startsWith(ROLE_PREFIX)) {
                    log.warn(
                            "Permission code '{}' uses forbidden ROLE_ namespace. Disqualifying from authority grant.",
                            code);
                    continue;
                }
                // Quyền hợp lệ được cấp nguyên văn theo mã permission
                authorities.add(new SimpleGrantedAuthority(code));
            }
        });

        return authorities;
    }

    /**
     * Trả về tên các vai trò đang hoạt động (không có tiền tố ROLE_), đã sắp xếp, để đưa vào response đăng
     * nhập. Áp dụng cùng quy tắc hợp lệ và xử lý trùng tên như {@link #resolveAuthorities}.
     */
    public List<String> resolveActiveRoleNames(Set<Role> roles) {
        return resolveValidRoles(roles).keySet().stream().sorted().toList();
    }

    /**
     * Chuẩn hóa và lọc vai trò dùng chung cho cả quyền lẫn tên vai trò:
     * 1. Role không hoạt động bị loại hoàn toàn (không có role authority, không có permission).
     * 2. Tên role: trim, uppercase (Locale.ROOT), tối đa một tiền tố ROLE_.
     * 3. Phần hậu tố phải khớp ^[A-Z0-9_]+$, nếu không thì loại.
     * 4. Nếu hai role ID khác nhau cùng chuẩn hóa về một tên thì loại cả hai.
     *
     * @return map từ hậu tố tên role (không có ROLE_) sang role hợp lệ duy nhất
     */
    private Map<String, Role> resolveValidRoles(Set<Role> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyMap();
        }

        // Gom vai trò hợp lệ theo tên đã chuẩn hóa, mỗi tên giữ các role ID khác nhau
        Map<String, Map<UUID, Role>> bySuffix = new HashMap<>();
        for (Role role : roles) {
            // Bỏ vai trò rỗng hoặc đã bị vô hiệu
            if (role == null || !role.isActive()) {
                continue;
            }
            String roleName = role.getRoleName();
            if (roleName == null || roleName.trim().isEmpty()) {
                log.warn("Encountered role with null or blank roleName for roleId: {}", role.getRoleId());
                continue;
            }
            // Chuẩn hóa: cắt khoảng trắng, in hoa, bỏ tiền tố ROLE_ nếu có
            String trimmed = roleName.trim().toUpperCase(Locale.ROOT);
            String suffix = trimmed.startsWith(ROLE_PREFIX) ? trimmed.substring(ROLE_PREFIX.length()) : trimmed;
            if (suffix.isEmpty() || !ROLE_SUFFIX_PATTERN.matcher(suffix).matches()) {
                log.warn("Invalid role name '{}' for roleId: {}, skipping", roleName, role.getRoleId());
                continue;
            }
            bySuffix.computeIfAbsent(suffix, k -> new HashMap<>()).putIfAbsent(role.getRoleId(), role);
        }

        // Hai role ID khác nhau trùng tên là mơ hồ nên loại cả hai
        Map<String, Role> valid = new HashMap<>();
        bySuffix.forEach((suffix, byId) -> {
            if (byId.size() > 1) {
                log.warn(
                        "Role authority collision detected for '{}{}' across role IDs: {}. Disqualifying colliding roles.",
                        ROLE_PREFIX,
                        suffix,
                        byId.keySet());
                return;
            }
            valid.put(suffix, byId.values().iterator().next());
        });
        return valid;
    }
}
