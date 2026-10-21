package com.aquasuite.ims.module.iam.internal.registration.validation;

import com.aquasuite.ims.module.iam.internal.registration.payload.RegisterRequest;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.nio.charset.StandardCharsets;

/** Validates cross-field confirmation and the BCrypt UTF-8 byte limit. */
public class RegistrationValidator
        implements ConstraintValidator<ValidRegistration, RegisterRequest> {
    @Override
    public boolean isValid(RegisterRequest request, ConstraintValidatorContext context) {
        if (request == null) return true;
        String password = request.password();
        boolean valid = true;
        context.disableDefaultConstraintViolation();
        if (password != null
                && (password.getBytes(StandardCharsets.UTF_8).length > 72
                        || !password.matches(
                                "(?s)(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[^A-Za-z0-9\\s]).{8,}"))) {
            context.buildConstraintViolationWithTemplate(
                            "Password must contain uppercase, lowercase, numbers and special characters; maximum 72 UTF-8 bytes")
                    .addPropertyNode("password")
                    .addConstraintViolation();
            valid = false;
        }
        if (password != null && !password.equals(request.passwordConfirmation())) {
            context.buildConstraintViolationWithTemplate("Password confirmation does not match")
                    .addPropertyNode("passwordConfirmation")
                    .addConstraintViolation();
            valid = false;
        }
        return valid;
    }
}
