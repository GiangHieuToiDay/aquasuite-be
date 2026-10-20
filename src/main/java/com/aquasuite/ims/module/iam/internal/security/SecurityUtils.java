package com.aquasuite.ims.module.iam.internal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiện ích truy xuất thông tin người dùng từ SecurityContext.
 */
public final class SecurityUtils {

    private static final ThreadLocal<Long> MOCK_USER_ID = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> MOCK_IS_ADMIN = new ThreadLocal<>();

    private SecurityUtils() {}

    /**
     * Dùng để thiết lập ID người dùng giả lập trong môi trường Test hoặc khi chưa có Auth.
     */
    public static void setMockUserId(Long userId) {
        MOCK_USER_ID.set(userId);
    }

    /**
     * Dùng để thiết lập cờ Admin giả lập trong môi trường Test.
     */
    public static void setMockIsAdmin(Boolean isAdmin) {
        MOCK_IS_ADMIN.set(isAdmin);
    }

    public static void clear() {
        MOCK_USER_ID.remove();
        MOCK_IS_ADMIN.remove();
    }

    /**
     * Lấy ID của người dùng hiện tại đang đăng nhập.
     */
    public static Long getCurrentUserId() {
        if (MOCK_USER_ID.get() != null) {
            return MOCK_USER_ID.get();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            Object principal = authentication.getPrincipal();
            if (principal instanceof Long id) {
                return id;
            }
            if (principal instanceof String idStr) {
                try {
                    return Long.parseLong(idStr);
                } catch (NumberFormatException ignored) {}
            }
        }

        // =========================================================================
        // MOCK TẠM THỜI (Khi chưa ghép nối JWT Filter):
        // Mặc định trả về 1L để các API yêu cầu Auth có thể chạy được.
        // =========================================================================
        return 1L;
    }

    /**
     * Kiểm tra xem người dùng hiện tại có vai trò Quản trị viên (Admin) hay không.
     */
    public static boolean isCurrentUserAdmin() {
        if (MOCK_IS_ADMIN.get() != null) {
            return MOCK_IS_ADMIN.get();
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated() && !"anonymousUser".equals(authentication.getPrincipal())) {
            return authentication.getAuthorities().stream()
                    .anyMatch(auth -> "ROLE_ADMIN".equalsIgnoreCase(auth.getAuthority())
                            || "ADMIN".equalsIgnoreCase(auth.getAuthority()));
        }

        // Mặc định tài khoản ID = 1L là Admin hệ thống trong môi trường chưa gắn JWT Filter
        Long currentUserId = getCurrentUserId();
        return currentUserId != null && currentUserId == 1L;
    }
}
