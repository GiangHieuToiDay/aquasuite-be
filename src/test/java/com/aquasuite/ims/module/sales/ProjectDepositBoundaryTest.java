package com.aquasuite.ims.module.sales;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.aquasuite.ims.module.sales.internal.service.ProjectDepositService;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class ProjectDepositBoundaryTest {
    @Test
    void uc29AndItsAdaptersUseOnlyPublicCrossModuleContracts() throws Exception {
        var productionClasses = java.nio.file.Path.of(ProjectDepositService.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        var classes = new ClassFileImporter().importPath(productionClasses);
        for (var type : classes) {
            String name = type.getSimpleName();
            if (!name.startsWith("ProjectDeposit") && !name.startsWith("ProjectReservation") && !name.startsWith("DemoProject")
                    && !name.startsWith("OrderStockReservation") && !name.startsWith("ReservationAllocation")
                    && !name.startsWith("DepositReservationPayment")
                    && !name.startsWith("StockReservation") && !name.startsWith("SkuReservationStock")) continue;
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String own = type.getPackageName(), other = dependency.getTargetClass().getPackageName();
                if (!other.startsWith("com.aquasuite.ims.module.")) continue;
                if (!own.split("[.]")[4].equals(other.split("[.]")[4])) {
                    assertThat(other).as(type.getName()+" -> "+dependency.getTargetClass().getName()).contains(".api");
                }
            }
        }
    }
}
