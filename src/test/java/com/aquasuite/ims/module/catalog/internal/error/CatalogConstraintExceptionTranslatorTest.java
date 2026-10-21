package com.aquasuite.ims.module.catalog.internal.error;

import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.assertj.core.api.Assertions.assertThat;

class CatalogConstraintExceptionTranslatorTest {

    @Test
    @DisplayName("Metadata: Translates exact Hibernate constraint names accurately")
    void testHibernateConstraintTranslation() {
        ConstraintViolationException cveCode = new ConstraintViolationException(
                "duplicate key", new SQLException(), "uq_categories_code_lower");
        AppException exCode = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveCode);
        assertThat(exCode.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_CODE_DUPLICATE);

        ConstraintViolationException cveRootName = new ConstraintViolationException(
                "duplicate key", new SQLException(), "uq_categories_root_name_lower");
        AppException exRoot = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveRootName);
        assertThat(exRoot.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);

        ConstraintViolationException cveParentName = new ConstraintViolationException(
                "duplicate key", new SQLException(), "uq_categories_parent_name_lower");
        AppException exParentName = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveParentName);
        assertThat(exParentName.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_NAME_DUPLICATE);

        ConstraintViolationException cveFkParent = new ConstraintViolationException(
                "violates foreign key", new SQLException(), "fk_categories_parent");
        AppException exFkParent = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveFkParent);
        assertThat(exFkParent.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_PARENT_NOT_FOUND);

        AppException exDeleteFk = CatalogConstraintExceptionTranslator.translateCategoryDeleteException(cveFkParent);
        assertThat(exDeleteFk.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_HAS_CHILDREN);

        ConstraintViolationException cveSelf = new ConstraintViolationException(
                "check constraint", new SQLException(), "chk_categories_not_self_parent");
        AppException exSelf = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveSelf);
        assertThat(exSelf.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_SELF_PARENT);

        ConstraintViolationException cveStatus = new ConstraintViolationException(
                "check constraint", new SQLException(), "chk_categories_status");
        AppException exStatus = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(cveStatus);
        assertThat(exStatus.getErrorCode()).isEqualTo(CatalogErrorCode.CATEGORY_STATUS_INVALID);

        // Brand constraints
        ConstraintViolationException cveBrandCode = new ConstraintViolationException(
                "duplicate key", new SQLException(), "uq_brands_code_lower");
        AppException exBrandCode = CatalogConstraintExceptionTranslator.translateBrandCreateOrUpdateException(cveBrandCode);
        assertThat(exBrandCode.getErrorCode()).isEqualTo(CatalogErrorCode.BRAND_CODE_DUPLICATE);

        ConstraintViolationException cveBrandName = new ConstraintViolationException(
                "duplicate key", new SQLException(), "uq_brands_name_lower");
        AppException exBrandName = CatalogConstraintExceptionTranslator.translateBrandCreateOrUpdateException(cveBrandName);
        assertThat(exBrandName.getErrorCode()).isEqualTo(CatalogErrorCode.BRAND_NAME_DUPLICATE);
    }

    @Test
    @DisplayName("Metadata: Unknown or null constraint metadata falls back strictly to DB_001")
    void testUnknownConstraintFallsBackToDb001() {
        ConstraintViolationException unknownCve = new ConstraintViolationException(
                "violates some unknown check", new SQLException(), "unknown_constraint_xyz");
        AppException exUnknown = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(unknownCve);
        assertThat(exUnknown.getErrorCode()).isEqualTo(CommonErrorCode.DATA_INTEGRITY_VIOLATION);

        SQLException rawSqlException = new SQLException("Some error with no structured metadata");
        AppException exRaw = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(rawSqlException);
        assertThat(exRaw.getErrorCode()).isEqualTo(CommonErrorCode.DATA_INTEGRITY_VIOLATION);

        AppException exNull = CatalogConstraintExceptionTranslator.translateCategoryCreateOrUpdateException(null);
        assertThat(exNull.getErrorCode()).isEqualTo(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
    }

    @Test
    @DisplayName("Constraint name normalization handles quotes and schema prefixes")
    void testConstraintNormalization() {
        assertThat(CatalogConstraintExceptionTranslator.normalizeConstraintName("\"public\".\"chk_categories_status\""))
                .isEqualTo("chk_categories_status");
        assertThat(CatalogConstraintExceptionTranslator.normalizeConstraintName("PUBLIC.UQ_CATEGORIES_CODE_LOWER"))
                .isEqualTo("uq_categories_code_lower");
        assertThat(CatalogConstraintExceptionTranslator.normalizeConstraintName(null)).isNull();
    }
}
