package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.controller.CodController;
import com.aquasuite.ims.module.finance.internal.repository.CodRepository;
import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryCodQueryService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CodBoundaryTest {
    @Test void codCrossModuleDependenciesUseOnlyPublicContracts() {
        var classes=new ClassFileImporter().importClasses(CodController.class,CodService.class,CodRepository.class,
                DeliveryCodQueryService.class);
        for (var type:classes) {
            String own=type.getPackageName().split("\\.")[4];
            for (var dependency:type.getDirectDependenciesFromSelf()) {
                String target=dependency.getTargetClass().getPackageName();
                if (target.startsWith("com.aquasuite.ims.module.") && !target.startsWith("com.aquasuite.ims.module."+own+"."))
                    assertThat(target).as(dependency.getDescription()).contains(".api").doesNotContain(".internal");
            }
        }
    }
}
