package com.aquasuite.ims.module.sales.internal.payload.request;

import com.aquasuite.ims.module.sales.internal.calculator.QuotationConstants;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.math.BigDecimal;

public class AllowedVatRateValidator implements ConstraintValidator<AllowedVatRate, BigDecimal> {

    @Override
    public boolean isValid(BigDecimal value, ConstraintValidatorContext context) {
        if (value == null) {
            // Null value is allowed if optional (default vatRate will be applied if needed)
            return true;
        }
        return QuotationConstants.isValidVatRate(value);
    }
}
