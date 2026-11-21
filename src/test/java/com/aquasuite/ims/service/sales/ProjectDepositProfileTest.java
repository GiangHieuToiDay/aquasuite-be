package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.config.finance.dev.*;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.catalog.CatalogLookupService;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.config.sales.dev.DemoProjectReservationOrderFactory;
import com.aquasuite.ims.config.inventory.dev.*;
import com.aquasuite.ims.controller.sales.OrderReservationController;
import com.aquasuite.ims.config.sales.dev.ProjectDepositDemoConfiguration;
import com.aquasuite.ims.repository.sales.*;
import com.aquasuite.ims.service.sales.impl.ProjectDepositService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDepositProfileTest {
    private ApplicationContextRunner context() {
        return new ApplicationContextRunner().withUserConfiguration(ProjectDepositDemoConfiguration.class,
                ProjectDepositService.class, OrderReservationController.class, DemoProjectReservationOrderFactory.class, DemoProjectDepositPaymentService.class,
                DemoProjectDepositController.class, DemoProjectReservationService.class, DemoProjectStockController.class)
                .withBean(IamQueryService.class, () -> mock(IamQueryService.class))
                .withBean(CatalogLookupService.class, () -> mock(CatalogLookupService.class))
                .withBean(CustomerService.class, () -> mock(CustomerService.class))
                .withBean(ProjectDepositOrderRepository.class, () -> mock(ProjectDepositOrderRepository.class))
                .withBean(ProjectDepositOrderItemRepository.class, () -> mock(ProjectDepositOrderItemRepository.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"default", "prod", "uc29-demo,prod", "uc29-demo,production"})
    void noPrototypeEndpointsOrSimulatorsOutsideExplicitNonProductionProfile(String profiles) {
        context().withPropertyValues("spring.profiles.active="+profiles).run(c -> {
            assertThat(c).doesNotHaveBean(ProjectDepositService.class).doesNotHaveBean(OrderReservationController.class).doesNotHaveBean(DemoProjectReservationOrderFactory.class)
                    .doesNotHaveBean(DemoProjectDepositPaymentService.class).doesNotHaveBean(DemoProjectReservationService.class)
                    .doesNotHaveBean(DemoProjectDepositController.class).doesNotHaveBean(DemoProjectStockController.class);
        });
    }

    @Test
    void explicitDemoProfileWiresTheCompletePrototype() {
        context().withPropertyValues("spring.profiles.active=uc29-demo").run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(OrderReservationController.class)
                    .hasSingleBean(DemoProjectDepositPaymentService.class).hasSingleBean(DemoProjectReservationService.class);
        });
    }
}
