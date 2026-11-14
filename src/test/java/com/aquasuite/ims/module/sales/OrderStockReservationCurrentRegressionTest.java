package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.catalog.api.CatalogApi;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Run the unchanged UC29 suite with the current inventory service dependency supplied. */
class OrderStockReservationCurrentRegressionTest extends OrderStockReservationPostgresTest {
    @MockBean CatalogApi catalog;
}
