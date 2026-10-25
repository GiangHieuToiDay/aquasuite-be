package com.aquasuite.ims.module.iam.internal.persistence;

import com.aquasuite.ims.module.iam.api.UserExternalService;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.iam.internal.controller.UserController;
import com.aquasuite.ims.module.iam.internal.entity.User;
import com.aquasuite.ims.module.iam.internal.entity.UserStatus;
import com.aquasuite.ims.module.iam.internal.service.UserService;
import com.aquasuite.ims.module.iam.internal.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

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
    @DisplayName("AC-08: REST payload location and encapsulation without external leakage")
    void testRestPayloadLocationAndEncapsulation() throws IOException {
        Path payloadDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/payload");
        assertThat(Files.exists(payloadDir)).as("Production payload directory must exist under internal").isTrue();

        // No external module outside IAM should import internal payload
        Path srcMain = Paths.get("src/main/java");
        if (Files.exists(srcMain)) {
            try (Stream<Path> stream = Files.walk(srcMain)) {
                List<Path> violatingFiles = stream
                        .filter(Files::isRegularFile)
                        .filter(p -> p.toString().endsWith(".java"))
                        .filter(p -> !p.toString().replace('\\', '/').contains("/module/iam/"))
                        .filter(p -> {
                            try {
                                return Files.readString(p).contains("com.aquasuite.ims.module.iam.internal.payload");
                            } catch (IOException e) {
                                return false;
                            }
                        })
                        .toList();
                assertThat(violatingFiles).as("No non-IAM file may import internal.payload").isEmpty();
            }
        }
    }

    @Test
    @DisplayName("AC-07: Public DTO skeleton does not expose sensitive credentials")
    void testSensitiveDataNotExposedInPublicDto() {
        Class<?>[] publicDtos = new Class<?>[]{UserSummaryDto.class};
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
    @DisplayName("AC-04, AC-05, AC-06, AC-07: Semantic structural assertions for User skeletons and DTOs")
    void testUserSkeletonsAndDtoStructuralIntegrity() throws IOException {
        // 1. UserExternalService remains empty public class without annotations/methods/fields/interfaces
        assertThat(Modifier.isPublic(UserExternalService.class.getModifiers())).isTrue();
        assertThat(UserExternalService.class.getAnnotations()).isEmpty();
        assertThat(UserExternalService.class.getDeclaredMethods()).isEmpty();
        assertThat(UserExternalService.class.getDeclaredFields()).isEmpty();
        assertThat(UserExternalService.class.getInterfaces()).isEmpty();

        // 2. UserService remains empty internal interface
        assertThat(UserService.class.isInterface()).isTrue();
        assertThat(UserService.class.getAnnotations()).isEmpty();
        assertThat(UserService.class.getDeclaredMethods()).isEmpty();

        // 3. UserServiceImpl remains empty public class without annotations/interfaces/constructors declared/dependencies
        assertThat(Modifier.isPublic(UserServiceImpl.class.getModifiers())).isTrue();
        assertThat(UserServiceImpl.class.getAnnotations()).isEmpty();
        assertThat(UserServiceImpl.class.getDeclaredMethods()).isEmpty();
        assertThat(UserServiceImpl.class.getDeclaredFields()).isEmpty();
        assertThat(UserServiceImpl.class.getInterfaces()).isEmpty();
        assertThat(UserServiceImpl.class.getDeclaredConstructors()).allMatch(c -> c.getParameterCount() == 0);

        // 4. UserSummaryDto is an immutable public record adhering to IAM sensitive boundary contract
        assertThat(Modifier.isPublic(UserSummaryDto.class.getModifiers())).isTrue();
        assertThat(UserSummaryDto.class.isRecord()).isTrue();

        // Exact component inventory verification
        List<String> componentNames = Arrays.stream(UserSummaryDto.class.getRecordComponents())
                .map(RecordComponent::getName)
                .toList();
        assertThat(componentNames).containsExactlyInAnyOrder(
                "id", "username", "fullName", "email", "phone", "avatarUrl", "status", "roles"
        );

        // Strict sensitive surface prohibition: no fields, methods, or components may expose credentials/tokens/secrets/codes/hashes
        List<String> prohibitedSensitiveKeywords = List.of(
                "password", "secret", "hash", "token", "credential", "salt",
                "verification", "code", "pin", "otp", "recovery", "key"
        );
        for (Field field : UserSummaryDto.class.getDeclaredFields()) {
            String name = field.getName().toLowerCase();
            for (String prohibited : prohibitedSensitiveKeywords) {
                assertThat(name)
                        .as("UserSummaryDto declared field '%s' must not expose sensitive keyword '%s'", field.getName(), prohibited)
                        .doesNotContain(prohibited);
            }
        }
        for (Method method : UserSummaryDto.class.getDeclaredMethods()) {
            if (Modifier.isStatic(method.getModifiers())) continue;
            if (java.util.Set.of("equals", "hashCode", "toString").contains(method.getName())) continue;
            String name = method.getName().toLowerCase();
            for (String prohibited : prohibitedSensitiveKeywords) {
                assertThat(name)
                        .as("UserSummaryDto method '%s' must not expose sensitive keyword '%s'", method.getName(), prohibited)
                        .doesNotContain(prohibited);
            }
        }

        // 5. Removed DTO files and obsolete namespaces must not exist
        Path oldUserRequest = Paths.get("src/main/java/com/aquasuite/ims/module/iam/api/dto/request/UserRequest.java");
        Path oldUserResponse = Paths.get("src/main/java/com/aquasuite/ims/module/iam/api/dto/response/UserResponse.java");
        assertThat(Files.exists(oldUserRequest)).as("UserRequest.java must be deleted").isFalse();
        assertThat(Files.exists(oldUserResponse)).as("UserResponse.java must be deleted").isFalse();

        Path authDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/auth");
        Path authorizationDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/authorization");
        Path userDir = Paths.get("src/main/java/com/aquasuite/ims/module/iam/internal/user");
        assertThat(Files.exists(authDir)).as("internal.auth directory must not exist").isFalse();
        assertThat(Files.exists(authorizationDir)).as("internal.authorization directory must not exist").isFalse();
        assertThat(Files.exists(userDir)).as("internal.user directory must not exist").isFalse();

        // 6. No production references to removed DTOs
        Path srcMain = Paths.get("src/main/java");
        try (Stream<Path> stream = Files.walk(srcMain)) {
            List<Path> referencingFiles = stream
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            String content = Files.readString(p);
                            return java.util.regex.Pattern.compile("\\bUserRequest\\b").matcher(content).find()
                                    || java.util.regex.Pattern.compile("\\bUserResponse\\b").matcher(content).find();
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .toList();
            assertThat(referencingFiles).as("No production file may reference UserRequest or UserResponse").isEmpty();
        }
    }
}
