package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.module.iam.api.dto.UserRequest;
import com.aquasuite.ims.module.iam.api.dto.UserResponse;
import com.aquasuite.ims.module.iam.internal.controller.UserController;
import com.aquasuite.ims.module.iam.internal.entity.UserEntity;
import com.aquasuite.ims.module.iam.internal.enums.UserStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.InputStream;
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
        public static TestUserSafeProjection from(UserEntity entity) {
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
        UserEntity user = new UserEntity();
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
    @DisplayName("AC-05, AC-11: No production DTO/mapper directories exist under iam.internal")
    void testNoProductionDtoOrMapperDirectoriesExist() {
        Path payloadDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/payload");
        Path mapperDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/mapper");

        assertThat(Files.exists(payloadDir)).as("Production payload directory must not exist").isFalse();
        assertThat(Files.exists(mapperDir)).as("Production mapper directory must not exist").isFalse();
    }

    @Test
    @DisplayName("AC-05: Existing public DTOs do not expose password_hash")
    void testSensitiveDataNotExposedInPublicDto() {
        Class<?>[] publicDtos = new Class<?>[]{UserRequest.class, UserResponse.class};
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
                "ca15b1007a1a1dc8715af5a39017cadebd9664659b17fcbc1ffd963d95e7e96b",

                "src/main/java/com/aquasuite/ims/module/iam/api/dto/UserRequest.java",
                "8e48fefc28471a3af46550b3a0d2aeecea1eb896b7e4cdec302ccfc925befea9",

                "src/main/java/com/aquasuite/ims/module/iam/api/dto/UserResponse.java",
                "932b8ca8a7efbf29a46b9fc4eb6bb2bbcfd208e83e936c20f50040444c2c42c4",

                "src/main/java/com/aquasuite/ims/module/iam/internal/controller/UserController.java",
                "be71fca446b74a27adb82685b78de9abaf5db608b8dff6a50717d6295e84699c",

                "src/main/java/com/aquasuite/ims/module/iam/internal/service/UserService.java",
                "561594bc97bd9a4fb225fbbc16e01b6e048fe1516b3c5a66827c03686a632ac2",

                "src/main/java/com/aquasuite/ims/module/iam/internal/service/Impl/UserServiceImpl.java",
                "b0dedbf5d90a43eeae07be1991f277914008dbf80c1042186ba944a7a617a2bb"
        );

        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        for (Map.Entry<String, String> entry : expectedHashes.entrySet()) {
            Path path = Paths.get(entry.getKey());
            assertThat(Files.exists(path)).as("File must exist: " + entry.getKey()).isTrue();

            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            String actualHash = HexFormat.of().formatHex(hash).toLowerCase();

            assertThat(actualHash)
                    .as("SHA-256 mismatch for " + entry.getKey())
                    .isEqualTo(entry.getValue());
        }
    }
}
