package com.aquasuite.ims.module.sales.internal.payload.request;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Documented
@Constraint(validatedBy = AllowedVatRateValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface AllowedVatRate {

    String message() default "VAT rate must be one of: 0, 8, 10";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
