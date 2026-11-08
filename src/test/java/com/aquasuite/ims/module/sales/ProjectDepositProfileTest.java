package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.finance.internal.dev.*;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.sales.internal.dev.DemoProjectReservationOrderFactory;
import com.aquasuite.ims.module.inventory.internal.dev.*;
import com.aquasuite.ims.module.sales.internal.controller.ProjectDepositController;
import com.aquasuite.ims.module.sales.internal.dev.ProjectDepositDemoConfiguration;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.module.sales.internal.service.ProjectDepositService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDepositProfileTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(ProjectDepositDemoConfiguration.class,
                ProjectDepositService.class, ProjectDepositController.class, DemoProjectReservationOrderFactory.class, DemoProjectDepositPaymentApi.class,
                DemoProjectDepositController.class, DemoProjectReservationApi.class, DemoProjectStockController.class)
                .withBean(IamApi.class, () -> mock(IamApi.class))
                .withBean(CatalogApi.class, () -> mock(CatalogApi.class))
                .withBean(CrmApi.class, () -> mock(CrmApi.class))
                .withBean(ProjectDepositOrderRepository.class, () -> mock(ProjectDepositOrderRepository.class))
                .withBean(ProjectDepositOrderItemRepository.class, () -> mock(ProjectDepositOrderItemRepository.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "uc29-demo,prod", "uc29-demo,production"})
    void noPrototypeEndpointsOrSimulatorsOutsideExplicitNonProductionProfile(String profiles) {
        context().withPropertyValues("spring.profiles.active="+profiles).run(c -> {
            assertThat(c).doesNotHaveBean(ProjectDepositService.class).doesNotHaveBean(ProjectDepositController.class).doesNotHaveBean(DemoProjectReservationOrderFactory.class)
                    .doesNotHaveBean(DemoProjectDepositPaymentApi.class).doesNotHaveBean(DemoProjectReservationApi.class)
                    .doesNotHaveBean(DemoProjectDepositController.class).doesNotHaveBean(DemoProjectStockController.class);
        });
    }

    @Test
    void explicitDemoProfileWiresTheCompletePrototype() {
        context().withPropertyValues("spring.profiles.active=uc29-demo").run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(ProjectDepositController.class)
                    .hasSingleBean(DemoProjectDepositPaymentApi.class).hasSingleBean(DemoProjectReservationApi.class);
        });
    }
}
