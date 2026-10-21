package com.aquasuite.ims;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootConfiguration
@EnableAutoConfiguration
@ComponentScan(
        basePackages = "com.aquasuite.ims",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = "com\\.aquasuite\\.ims\\.module\\.iam\\.internal\\.(legacy|config|controller|repository|security|service)(\\..*)?"
        )
)
@EntityScan(basePackages = {
        "com.aquasuite.ims.module.iam.internal.user.entity",
        "com.aquasuite.ims.module.iam.internal.authorization.entity",
        "com.aquasuite.ims.module.audit.internal.entity",
        "com.aquasuite.ims.module.inventory.internal.entity",
        "com.aquasuite.ims.module.catalog.internal.entity"
})
@EnableJpaRepositories(basePackages = {
        "com.aquasuite.ims.module.iam.internal.user.repository",
        "com.aquasuite.ims.module.iam.internal.authorization.repository",
        "com.aquasuite.ims.module.audit.internal.repository",
        "com.aquasuite.ims.module.inventory.internal.repository",
        "com.aquasuite.ims.module.catalog.internal.repository"
})
public class AquasuiteBeApplication {

    public static void main(String[] args) {
        SpringApplication.run(AquasuiteBeApplication.class, args);
    }

}
