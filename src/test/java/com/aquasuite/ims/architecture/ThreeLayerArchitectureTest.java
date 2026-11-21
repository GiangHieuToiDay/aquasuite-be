package com.aquasuite.ims.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

public class ThreeLayerArchitectureTest {

    private static JavaClasses importedClasses;

    @BeforeAll
    static void init() {
        importedClasses = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages("com.aquasuite.ims");
    }

    @Test
    @DisplayName("Rule 1: Services must not depend on Controllers")
    void services_should_not_depend_on_controllers() {
        noClasses().that().resideInAPackage("com.aquasuite.ims.service..")
                .should().dependOnClassesThat().resideInAPackage("com.aquasuite.ims.controller..")
                .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 2: Entities must not depend on Controllers, Services, or Repositories")
    void entities_should_not_depend_on_higher_layers() {
        noClasses().that().resideInAPackage("com.aquasuite.ims.entity..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.aquasuite.ims.controller..",
                        "com.aquasuite.ims.service..",
                        "com.aquasuite.ims.repository.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 3: Repositories (excluding store) must not depend on Services or Controllers")
    void repositories_should_not_depend_on_services_or_controllers() {
        noClasses().that().resideInAPackage("com.aquasuite.ims.repository..")
                .and().resideOutsideOfPackage("com.aquasuite.ims.repository..store..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.aquasuite.ims.controller..",
                        "com.aquasuite.ims.service.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 4: DTOs must not depend on Controllers or Repositories")
    void dtos_should_not_depend_on_controllers_or_repositories() {
        noClasses().that().resideInAPackage("com.aquasuite.ims.dto..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.aquasuite.ims.controller..",
                        "com.aquasuite.ims.repository.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 5: Enums must not depend on Controllers, Services, Repositories, or Entities")
    void enums_should_not_depend_on_layers() {
        noClasses().that().resideInAPackage("com.aquasuite.ims.enums..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "com.aquasuite.ims.controller..",
                        "com.aquasuite.ims.service..",
                        "com.aquasuite.ims.repository..",
                        "com.aquasuite.ims.entity.."
                )
                .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 6: No classes must depend on legacy module or share packages")
    void no_classes_should_depend_on_legacy_packages() {
        noClasses().should().dependOnClassesThat().resideInAnyPackage(
                "com.aquasuite.ims.module..",
                "com.aquasuite.ims.share.."
        )
        .check(importedClasses);
    }

    @Test
    @DisplayName("Rule 7: Entities must not have cross-domain JPA ORM relationships")
    void entities_must_not_have_cross_domain_orm_relations() {
        classes().that().resideInAPackage("com.aquasuite.ims.entity..")
                .should(notHaveCrossDomainOrmRelationships())
                .check(importedClasses);
    }

    private static ArchCondition<com.tngtech.archunit.core.domain.JavaClass> notHaveCrossDomainOrmRelationships() {
        return new ArchCondition<>("not have cross-domain JPA relationships") {
            @Override
            public void check(com.tngtech.archunit.core.domain.JavaClass javaClass, ConditionEvents events) {
                String pkg = javaClass.getPackageName();
                String[] parts = pkg.split("\\.");
                if (parts.length < 5) return;
                String domain = parts[4];

                for (JavaField field : javaClass.getAllFields()) {
                    boolean isOrmRelation = field.isAnnotatedWith(ManyToOne.class)
                            || field.isAnnotatedWith(OneToMany.class)
                            || field.isAnnotatedWith(ManyToMany.class)
                            || field.isAnnotatedWith(OneToOne.class);

                    if (isOrmRelation) {
                        String targetPkg = field.getRawType().getPackageName();
                        if (targetPkg.startsWith("com.aquasuite.ims.entity.")) {
                            String[] targetParts = targetPkg.split("\\.");
                            if (targetParts.length >= 5) {
                                String targetDomain = targetParts[4];
                                if (!domain.equals(targetDomain)) {
                                    String message = String.format("Entity %s.%s has cross-domain ORM relation to %s (%s -> %s)",
                                            javaClass.getName(), field.getName(), field.getRawType().getName(), domain, targetDomain);
                                    events.add(SimpleConditionEvent.violated(field, message));
                                }
                            }
                        }
                    }
                }
            }
        };
    }
}
