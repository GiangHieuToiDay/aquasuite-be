package com.aquasuite.ims.module.iam.internal.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Tiện ích truy xuất thông tin người dùng từ SecurityContext.
 */
public final class SecurityUtils {

    private static final ThreadLocal<Long> MOCK_USER_ID = new ThreadLocal<>();

    private SecurityUtils() {}

    /**
     * Dùng để thiết lập ID người dùng giả lập trong môi trường Test hoặc khi chưa có Auth.
     */
    public static void setMockUserId(Long userId) {
        MOCK_USER_ID.set(userId);
    }

    public static void clear() {
        MOCK_USER_ID.remove();
    }

    /**
     * Lấy ID của người dùng hiện tại đang đăng nhập.
     *
     * TODO: Khi bạn cùng nhóm hoàn thành module Auth (nạp UserPrincipal vào SecurityContextHolder),
     * chỉ cần xóa phần mock fallback (1L) ở dưới và lấy trực tiếp từ Authentication principal.
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
        // Mặc định trả về 1L để các API yêu cầu Auth (như /change-password) có thể chạy được.
        // Khi bạn cùng nhóm hoàn thành Auth, thay dòng này bằng việc trả về null hoặc ném UnauthorizedException.
        // =========================================================================
        return 1L;
    }
}
