package com.aquasuite.ims.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Set;

/**
 * Cấu hình bảo mật cho toàn bộ API: stateless dùng access JWT (Resource Server), chỉ mở các endpoint
 * đăng nhập, làm mới token và đăng ký, mọi endpoint còn lại phải xác thực. Khai báo thêm bộ mã hóa
 * mật khẩu BCrypt, provider đăng nhập và AuthenticationManager; bật phân quyền theo method
 * ({@code @PreAuthorize}).
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class AuthSecurityConfig {

    private final JwtDecoderConfig jwtDecoderConfig;
    private final AccessAuthenticationConverter accessAuthenticationConverter;
    private final AuthSecurityErrorHandlers authSecurityErrorHandlers;
    private final UserDetailsServiceImpl userDetailsService;

    /**
     * Danh sách các endpoint công khai mà BearerTokenAuthenticationFilter phải bỏ qua
     * header Authorization (nếu có). Nếu không bỏ qua, client gửi kèm stale/expired
     * Bearer token lên /auth/refresh sẽ bị filter chặn 401 trước khi vào controller.
     */
    private static final Set<String> PUBLIC_AUTH_PATHS = Set.of(
            "/api/v1/auth/login",
            "/api/v1/auth/refresh",
            "/api/v1/iam/register",
            "/api/v1/iam/register/verifications",
            "/api/v1/iam/register/otp-resends"
    );

    /** Bộ mã hóa mật khẩu BCrypt với cost 12 cho mật khẩu tài khoản. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    /** Kết nối UserDetailsService và PasswordEncoder vào provider đăng nhập. */
    @Bean
    public DaoAuthenticationProvider daoAuthenticationProvider(PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    /** Lấy AuthenticationManager do Spring Security quản lý. */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }

    /**
     * Bọc DefaultBearerTokenResolver để trả null cho các endpoint công khai,
     * ngăn BearerTokenAuthenticationFilter chặn request có stale/expired Bearer header.
     */
    @Bean
    public BearerTokenResolver publicPathAwareBearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> {
            String uri = request.getRequestURI();
            if (PUBLIC_AUTH_PATHS.contains(uri)) {
                return null;
            }
            return delegate.resolve(request);
        };
    }

    /**
     * Cấu hình security stateless: chỉ mở endpoint đăng nhập/refresh/đăng ký,
     * yêu cầu JWT cho các endpoint còn lại và chuẩn hóa response lỗi bảo mật.
     * bearerTokenResolver bỏ qua Bearer header trên endpoint công khai để
     * BearerTokenAuthenticationFilter không chặn 401 trước permitAll().
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                // API stateless dùng JWT trong header nên tắt CSRF và không dùng cookie phiên
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // Các endpoint công khai; mọi request còn lại phải có JWT hợp lệ
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/iam/register", "/api/v1/iam/register/verifications", "/api/v1/iam/register/otp-resends").permitAll()
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/sales/catalog-items",
                                "/api/v1/catalog/sales-items",
                                "/api/v1/catalog/storefront/**"
                        ).permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/logout").authenticated()
                        .anyRequest().authenticated()
                )
                // Resource Server: giải mã JWT bằng JwtDecoderConfig rồi dựng quyền từ cơ sở dữ liệu
                .oauth2ResourceServer(oauth2 -> oauth2
                        .bearerTokenResolver(publicPathAwareBearerTokenResolver())
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoderConfig)
                                .jwtAuthenticationConverter(accessAuthenticationConverter)
                        )
                        .authenticationEntryPoint(authSecurityErrorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(authSecurityErrorHandlers.accessDeniedHandler())
                )
                // Chuẩn hóa response 401/403 cho các lỗi ngoài luồng Resource Server
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authSecurityErrorHandlers.authenticationEntryPoint())
                        .accessDeniedHandler(authSecurityErrorHandlers.accessDeniedHandler())
                );

        return http.build();
    }
}
