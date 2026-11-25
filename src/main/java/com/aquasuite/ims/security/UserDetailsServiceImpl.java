package com.aquasuite.ims.security;

import com.aquasuite.ims.entity.iam.User;
import com.aquasuite.ims.repository.iam.UserRepository;
import com.aquasuite.ims.service.iam.impl.CurrentAuthoritiesService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Nạp người dùng theo email cho bước đăng nhập bằng mật khẩu của Spring Security. Kết quả là
 * {@link AppUserDetails} chứa luôn entity và quyền để {@code AuthServiceImpl} dùng lại.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final CurrentAuthoritiesService currentAuthoritiesService;

    /** Tìm người dùng theo email (không phân biệt hoa thường), nạp kèm vai trò và quyền. */
    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new UsernameNotFoundException("Email must not be empty");
        }

        User user = userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));

        return new AppUserDetails(user, currentAuthoritiesService.resolveAuthorities(user.getRoles()));
    }
}
