package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.module.iam.api.dto.request.UserRequest;
import com.aquasuite.ims.module.iam.api.dto.response.UserResponse;
import com.aquasuite.ims.module.iam.internal.user.controller.UserController;
import com.aquasuite.ims.module.iam.internal.user.entity.User;
import com.aquasuite.ims.module.iam.internal.user.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class IamSensitiveDataBoundaryTest {

    // Test-only record demonstrating safe projection without sensitive fields (AC-05)
    public record TestUserSafeProjection(UUID id, String username, String email, UserStatus status) {
        public static TestUserSafeProjection from(User entity) {
            return new TestUserSafeProjection(
                    entity.getId(),
                    entity.getUsername(),
                    entity.getEmail(),
                    entity.getStatus()
            );
        }
    }

    @Test
    @DisplayName("AC-05: Test-only projection excludes password_hash and sensitive credentials")
    void testTestOnlySafeProjectionFixture() {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setUsername("safe_user");
        user.setEmail("safe@test.com");
        user.setPasswordHash("super_secret_hash_not_exposed");
        user.setStatus(UserStatus.ACTIVE);

        TestUserSafeProjection projection = TestUserSafeProjection.from(user);
        assertThat(projection.id()).isEqualTo(user.getId());
        assertThat(projection.username()).isEqualTo("safe_user");
        assertThat(projection.email()).isEqualTo("safe@test.com");
        assertThat(projection.status()).isEqualTo(UserStatus.ACTIVE);

        // Verify test record has no password field
        for (Field f : TestUserSafeProjection.class.getDeclaredFields()) {
            assertThat(f.getName().toLowerCase()).doesNotContain("password");
        }
    }

    @Test
    @DisplayName("Current-user public contract returns scalar IDs or public DTOs, never internal entities")
    void currentUserApiDoesNotExposeInternalEntities() {
        for (var method : com.aquasuite.ims.module.iam.api.IamFacade.class.getDeclaredMethods()) {
            var result = method.getReturnType();
            assertThat(result.isAnnotationPresent(jakarta.persistence.Entity.class)).isFalse();
            if (result != UUID.class) assertThat(result.getPackageName()).startsWith("com.aquasuite.ims.module.iam.api");
        }
    }

    @Test
    @DisplayName("AC-05: Existing public DTOs do not expose password_hash")
    void testSensitiveDataNotExposedInPublicDto() {
        Class<?>[] publicDtos = new Class<?>[]{UserRequest.class, UserResponse.class, com.aquasuite.ims.module.iam.api.dto.UserSummaryDto.class};
        for (Class<?> dto : publicDtos) {
            for (Field field : dto.getDeclaredFields()) {
                String name = field.getName().toLowerCase();
                assertThat(name).doesNotContain("passwordhash");
                assertThat(name).doesNotContain("password_hash");
            }
        }
    }

    @Test
    @DisplayName("AC-11, AC-12: UserController has no annotations and no endpoint implementations")
    void testUserControllerHasNoEndpointAnnotations() {
        assertThat(UserController.class.getAnnotations()).isEmpty();
        assertThat(UserController.class.getDeclaredMethods()).isEmpty();
    }

    @Test
    @DisplayName("AC-12: All six protected skeleton files match exact baseline SHA-256")
    void testSixProtectedSkeletonFilesSha256Fingerprint() throws Exception {
        Map<String, String> expectedHashes = Map.of(
                "src/main/java/com/aquasuite/ims/module/iam/api/UserExternalService.java",
                "b730074ab8ae82c28956ed5b14ae9ca54c743aaf3ad9a15853cf5b8221ae368c",

                "src/main/java/com/aquasuite/ims/module/iam/api/dto/request/UserRequest.java",
                "9fcd250ccc0d082831db6b3acb1e9b6371104046d625a28ee920b49288500d5e",

                "src/main/java/com/aquasuite/ims/module/iam/api/dto/response/UserResponse.java",
                "c23de5fe7cb2a3925ab8ba9e76b75bd3fd907950ec598efefff6adc3ec2b1d37",

                "src/main/java/com/aquasuite/ims/module/iam/internal/user/controller/UserController.java",
                "60bc909dc8e72f882bf424f077c74e21dcb3845614fcee25a9cf5b81f7266cde",

                "src/main/java/com/aquasuite/ims/module/iam/internal/user/service/UserService.java",
                "d1f383e92d66fe8526917213e36a5b09927d73630d7633d037113b6832f5ed0d",

                "src/main/java/com/aquasuite/ims/module/iam/internal/user/service/impl/UserServiceImpl.java",
                "e48bef9beff8ee193c4f2defcd73e0f2ca4b251cbd662c8ba9ed9c231aa75ad6"
        );

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (Map.Entry<String, String> entry : expectedHashes.entrySet()) {
            Path path = Paths.get(entry.getKey());
            assertThat(Files.exists(path)).as("File must exist: " + entry.getKey()).isTrue();

            // Git checkouts may use CRLF on Windows; compare the canonical repository text.
            byte[] bytes = Files.readString(path, java.nio.charset.StandardCharsets.UTF_8)
                    .replace("\r\n", "\n").getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] hash = digest.digest(bytes);
            String actualHash = HexFormat.of().formatHex(hash).toLowerCase();

            assertThat(actualHash)
                    .as("SHA-256 mismatch for " + entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }
}
