package com.aquasuite.ims.architecture;

import static org.assertj.core.api.Assertions.*;

import com.tngtech.archunit.core.importer.ClassFileImporter;

import org.junit.jupiter.api.Test;

class BundleCoreBoundaryTest {
    @Test
    void newCoreUsesPublicContractsOnly() {
        var classes = new ClassFileImporter().importPackages("com.aquasuite.ims.module");
        var names =
                java.util.Set.of(
                        "BundleAdminService",
                        "BundlePricingService",
                        "BundleCoreMapper",
                        "BundleAdminController",
                        "BundleDetailAssembler",
                        "BundleStorefrontService",
                        "BundleStorefrontController",
                        "BundleCoreMapperImpl",
                        "AuditService");
        for (var c : classes)
            if (names.contains(c.getSimpleName()))
                for (var dep : c.getDirectDependenciesFromSelf()) {
                    String origin = c.getPackageName(),
                            target = dep.getTargetClass().getPackageName();
                    if (!target.startsWith("com.aquasuite.ims.module.")) continue;
                    String own = origin.split("[.]")[4], other = target.split("[.]")[4];
                    if (!own.equals(other)) assertThat(target).contains(".api");
                    assertThat(dep.getTargetClass().getName())
                            .doesNotContain(
                                    "InventoryFacade", "SecurityUtils", "IamFacade", "Cloudinary");
                }
        // ProductBulkExcelImporter retains pre-existing event imports; only the added guard is in
        // scope.
    }
}
