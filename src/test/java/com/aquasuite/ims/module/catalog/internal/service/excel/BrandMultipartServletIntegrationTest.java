package com.aquasuite.ims.module.catalog.internal.service.excel;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.testsupport.AuthIntegrationTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(BrandMultipartServletIntegrationTest.TestSecurity.class)
class BrandMultipartServletIntegrationTest extends AuthIntegrationTestSupport {
    private static final int FIVE_MIB = 5 * 1024 * 1024;
    @LocalServerPort int port;
    @MockitoBean IamApi iam;
    @Autowired ObjectMapper mapper;

    @Test
    void multipartLimitsThroughRealServletContainer() throws Exception {
        when(iam.getCurrentUserId()).thenReturn(UUID.randomUUID());
        try (HttpClient client = HttpClient.newHttpClient()) {
            var brandAtLimit = post(client, "/api/v1/brands/import-jobs", "brands.xlsx", "application/octet-stream", FIVE_MIB);
            assertEquals(202, brandAtLimit.statusCode(), brandAtLimit.body());
            assertTrue(brandAtLimit.body().contains("jobId"));
            String jobId = mapper.readTree(brandAtLimit.body()).path("data").path("jobId").asText();
            boolean invalidFile = false;
            for (int attempt = 0; attempt < 40; attempt++) {
                HttpRequest progress = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port
                        + "/api/v1/brands/import-jobs/" + jobId)).GET().build();
                var snapshot = client.send(progress, HttpResponse.BodyHandlers.ofString());
                if (snapshot.body().contains("INVALID_FILE")) { invalidFile = true; break; }
                Thread.sleep(50);
            }
            assertTrue(invalidFile, "Unreadable XLSX must become FAILED / INVALID_FILE after acceptance");

            var brandOverLimit = post(client, "/api/v1/brands/import-jobs", "brands.xlsx", "application/octet-stream", FIVE_MIB + 1);
            assertEquals(413, brandOverLimit.statusCode(), brandOverLimit.body());
            assertTrue(brandOverLimit.body().contains("BRIMP_002"));

            var commonLimit = post(client, "/api/v1/brands/import-jobs", "brands.xlsx", "application/octet-stream", 6 * 1024 * 1024 + 1);
            assertEquals(413, commonLimit.statusCode(), commonLimit.body());
            assertTrue(commonLimit.body().contains("SYS_005"));

            var product = post(client, "/api/v1/catalog/import", "products.xlsx", "application/octet-stream", 2 * 1024 * 1024);
            assertEquals(202, product.statusCode(), product.body());

            var avatarAtLimit = post(client, "/api/v1/profile/avatar", "avatar.png", "image/png", FIVE_MIB);
            assertEquals(200, avatarAtLimit.statusCode(), avatarAtLimit.body());
            var avatarOverLimit = post(client, "/api/v1/profile/avatar", "avatar.png", "image/png", FIVE_MIB + 1);
            assertEquals(413, avatarOverLimit.statusCode(), avatarOverLimit.body());
            assertTrue(avatarOverLimit.body().contains("MSG17"));
        }
    }

    private HttpResponse<String> post(HttpClient client, String path, String filename, String type, int size) throws Exception {
        String boundary = "work015boundary";
        ByteArrayOutputStream body = new ByteArrayOutputStream(size + 512);
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + filename
                + "\"\r\nContent-Type: " + type + "\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
        body.write(new byte[size]);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.US_ASCII));
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray())).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @TestConfiguration
    static class TestSecurity {
        @Bean
        @Order(0)
        SecurityFilterChain multipartTestChain(HttpSecurity http) throws Exception {
            return http.securityMatcher("/api/v1/brands/import-jobs", "/api/v1/brands/import-jobs/**", "/api/v1/catalog/import", "/api/v1/profile/avatar")
                    .csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
                    .addFilterAfter(new OncePerRequestFilter() {
                        @Override protected void doFilterInternal(jakarta.servlet.http.HttpServletRequest request,
                                jakarta.servlet.http.HttpServletResponse response, jakarta.servlet.FilterChain chain)
                                throws java.io.IOException, jakarta.servlet.ServletException {
                            SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                                    "multipart-test-actor", "n/a", List.of(new SimpleGrantedAuthority("CATALOG_BRAND_CREATE"))));
                            chain.doFilter(request, response);
                        }
                    }, SecurityContextHolderFilter.class).build();
        }
    }
}
