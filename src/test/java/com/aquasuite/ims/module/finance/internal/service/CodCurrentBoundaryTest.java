package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.internal.service.impl.CodService;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryCodQueryService;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class CodCurrentBoundaryTest {
    @Test void newCodIntegrationUsesOnlyPublicContractsAcrossModules() {
        var classes = new ClassFileImporter().importClasses(CodService.class, TransactionRepository.class, DeliveryCodQueryService.class);
        for (var type : classes) {
            String owner = type.getPackageName().contains(".module.finance.")
                ? "com.aquasuite.ims.module.finance." : "com.aquasuite.ims.module.fulfillment.";
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getPackageName();
                if (target.startsWith("com.aquasuite.ims.module.") && !target.startsWith(owner)) {
                    assertThat(target).as(dependency.getDescription()).contains(".api").doesNotContain(".internal");
                }
            }
        }
    }
}
