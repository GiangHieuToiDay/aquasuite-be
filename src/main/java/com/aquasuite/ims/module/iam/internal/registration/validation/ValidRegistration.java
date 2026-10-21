package com.aquasuite.ims.module.iam.internal.registration.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = RegistrationValidator.class)
public @interface ValidRegistration {
    String message() default "Invalid registration form";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
