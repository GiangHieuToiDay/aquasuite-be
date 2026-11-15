package com.aquasuite.ims.module.fulfillment;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DeliveryBoundaryTest {
    @Test void fulfillmentUsesOnlyPublicContractsOfOtherModules() {
        var classes = new ClassFileImporter().importPackages("com.aquasuite.ims.module.fulfillment.internal");
        for (var type : classes) {
            for (var dependency : type.getDirectDependenciesFromSelf()) {
                String target = dependency.getTargetClass().getPackageName();
                if (target.startsWith("com.aquasuite.ims.module.")
                        && !target.startsWith("com.aquasuite.ims.module.fulfillment.")) {
                    assertThat(target).as(dependency.getDescription()).contains(".api").doesNotContain(".internal");
                }
            }
        }
    }
}
