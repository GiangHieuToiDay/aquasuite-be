package com.aquasuite.ims.module.iam.internal.registration;

import com.aquasuite.ims.module.iam.internal.payload.request.RegisterRequest;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.assertj.core.api.Assertions.assertThat;

class RegisterValidationTest {
    private Set<String> errors(RegisterRequest request) {
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            return factory.getValidator().validate(request).stream()
                    .map(v -> v.getPropertyPath().toString())
                    .collect(Collectors.toSet());
        }
    }

    @Test
    void validatesNormalizedVietnameseNameAndPreservesPassword() {
        var form =
                new RegisterRequest(
                        "  Nguyễn Văn An  ",
                        "  test@example.test ",
                        " 0901234567 ",
                        " Aa12345!",
                        " Aa12345!",
                        true);
        assertThat(errors(form)).isEmpty();
        assertThat(form.fullName()).isEqualTo("Nguyễn Văn An");
        assertThat(form.password()).startsWith(" ");
        assertThat(form.toString()).doesNotContain(form.password(), form.email());
    }

    @Test
    void reportsRequiredAndCrossFieldErrorsWithoutExposingValues() {
        assertThat(
                        errors(
                                new RegisterRequest(
                                        "A", "invalid", "+84901234567", "short", "other", false)))
                .contains(
                        "fullName",
                        "email",
                        "phone",
                        "password",
                        "passwordConfirmation",
                        "termsAccepted");
        assertThat(errors(new RegisterRequest(null, null, null, null, null, null)))
                .contains(
                        "fullName",
                        "email",
                        "phone",
                        "password",
                        "passwordConfirmation",
                        "termsAccepted");
    }

    @Test
    void checksNameLimitsAndUtf8BcryptByteLimit() {
        assertThat(errors(form("A".repeat(50), "Ab1!".repeat(18)))).isEmpty();
        assertThat(errors(form("A".repeat(51), "Ab12345!"))).contains("fullName");
        assertThat(errors(form("An", "Ab1!" + "ệ".repeat(23)))).contains("password");
        assertThat(errors(form("An", "abcdefgh1!"))).contains("password");
        assertThat(errors(form("An", "Abcdefg1 "))).contains("password");
    }

    private RegisterRequest form(String name, String password) {
        return new RegisterRequest(
                name, "test@example.test", "0901234567", password, password, true);
    }
}
