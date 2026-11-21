package com.aquasuite.ims.repository.sales;

import com.aquasuite.ims.service.catalog.CatalogLookupService;
import org.springframework.boot.test.mock.mockito.MockBean;

/** Run the unchanged UC29 suite with the current inventory service dependency supplied. */
class OrderStockReservationCurrentRegressionTest extends OrderStockReservationPostgresTest {
    @MockBean CatalogLookupService catalog;
}
