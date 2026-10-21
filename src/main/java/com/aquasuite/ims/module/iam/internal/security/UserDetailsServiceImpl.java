package com.aquasuite.ims.module.iam.internal.security;

import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.repository.UserRepository;
import com.aquasuite.ims.module.iam.internal.service.impl.CurrentAuthoritiesService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Slf4j
@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private final UserRepository userRepository;
    private final CurrentAuthoritiesService currentAuthoritiesService;

    public UserDetailsServiceImpl(
            @org.springframework.beans.factory.annotation.Autowired(required = false) UserRepository userRepository,
            CurrentAuthoritiesService currentAuthoritiesService
    ) {
        this.userRepository = userRepository;
        this.currentAuthoritiesService = currentAuthoritiesService;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        if (email == null || email.trim().isEmpty()) {
            throw new UsernameNotFoundException("Email must not be empty");
        }

        if (userRepository == null) {
            throw new UsernameNotFoundException("User repository is unavailable");
        }

        User user = userRepository.findWithRolesAndPermissionsByEmailIgnoreCase(email.trim())
                .orElseThrow(() -> new UsernameNotFoundException("User not found with email: " + email));

        boolean enabled = (user.getStatus() == UserStatus.ACTIVE);
        Collection<GrantedAuthority> authorities = currentAuthoritiesService.resolveAuthorities(user.getRoles());

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPasswordHash())
                .disabled(!enabled)
                .accountExpired(false)
                .credentialsExpired(false)
                .accountLocked(false)
                .authorities(authorities)
                .build();
    }
}
