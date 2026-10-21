package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.hibernate.exception.ConstraintViolationException;

import java.util.Locale;

public final class CatalogConstraintExceptionTranslator {

    private CatalogConstraintExceptionTranslator() {}

    public static String extractConstraintName(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof ConstraintViolationException cve) {
                String name = cve.getConstraintName();
                if (name != null && !name.isBlank()) {
                    return normalizeConstraintName(name);
                }
            }
            String psqlConstraint = extractFromPsqlException(current);
            if (psqlConstraint != null && !psqlConstraint.isBlank()) {
                return normalizeConstraintName(psqlConstraint);
            }
            current = current.getCause();
        }
        return null;
    }

    private static String extractFromPsqlException(Throwable throwable) {
        if (throwable != null && "org.postgresql.util.PSQLException".equals(throwable.getClass().getName())) {
            try {
                java.lang.reflect.Method getMsgMethod = throwable.getClass().getMethod("getServerErrorMessage");
                Object serverMsg = getMsgMethod.invoke(throwable);
                if (serverMsg != null) {
                    java.lang.reflect.Method getConstraintMethod = serverMsg.getClass().getMethod("getConstraint");
                    Object constraint = getConstraintMethod.invoke(serverMsg);
                    if (constraint instanceof String s && !s.isBlank()) {
                        return s;
                    }
                }
            } catch (Exception ignored) {
                // fall through
            }
        }
        return null;
    }

    public static String normalizeConstraintName(String raw) {
        if (raw == null) {
            return null;
        }
        String cleaned = raw.trim();
        int lastDot = cleaned.lastIndexOf('.');
        if (lastDot >= 0 && lastDot < cleaned.length() - 1) {
            cleaned = cleaned.substring(lastDot + 1);
        }
        cleaned = cleaned.replace("\"", "");
        return cleaned.toLowerCase(Locale.ROOT);
    }

    public static AppException translateCategoryCreateOrUpdateException(Throwable throwable) {
        String constraint = extractConstraintName(throwable);
        if (constraint == null) {
            return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        return switch (constraint) {
            case "chk_categories_code_format" -> new AppException(CatalogErrorCode.INVALID_CATEGORY_CODE);
            case "uq_categories_code_lower" -> new AppException(CatalogErrorCode.CATEGORY_CODE_DUPLICATE);
            case "uq_categories_root_name_lower", "uq_categories_parent_name_lower" -> new AppException(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);
            case "fk_categories_parent" -> new AppException(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND);
            case "chk_categories_not_self_parent" -> new AppException(CatalogErrorCode.CATEGORY_SELF_PARENT);
            case "chk_categories_status" -> new AppException(CatalogErrorCode.CATEGORY_STATUS_INVALID);
            case "chk_categories_name_not_blank" -> new AppException(CommonErrorCode.VALIDATION_ERROR);
            default -> new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        };
    }

    public static AppException translateCategoryDeleteException(Throwable throwable) {
        String constraint = extractConstraintName(throwable);
        if (constraint == null) {
            return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        return switch (constraint) {
            case "fk_categories_parent" -> new AppException(CatalogErrorCode.CATEGORY_HAS_CHILDREN);
            default -> new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        };
    }

    public static AppException translateBrandCreateOrUpdateException(Throwable throwable) {
        String constraint = extractConstraintName(throwable);
        if (constraint == null) {
            return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        return switch (constraint) {
            case "chk_brands_code_format" -> new AppException(CatalogErrorCode.INVALID_BRAND_CODE);
            case "uq_brands_code_lower" -> new AppException(CatalogErrorCode.BRAND_CODE_DUPLICATE);
            case "uq_brands_name_lower" -> new AppException(CatalogErrorCode.BRAND_NAME_DUPLICATE);
            case "chk_brands_status" -> new AppException(CatalogErrorCode.BRAND_STATUS_INVALID);
            case "chk_brands_name_not_blank" -> new AppException(CommonErrorCode.VALIDATION_ERROR);
            default -> new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        };
    }

    public static AppException translateBrandDeleteException(Throwable throwable) {
        return new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
    }
}
