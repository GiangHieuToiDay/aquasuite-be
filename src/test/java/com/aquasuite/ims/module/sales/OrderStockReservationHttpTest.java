package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.sales.internal.controller.OrderStockReservationController;
import com.aquasuite.ims.module.sales.internal.service.OrderStockReservationService;
import com.aquasuite.ims.share.exception.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.UUID;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class OrderStockReservationHttpTest {
    @Test void rejectsMissingKeysInvalidQuantitiesAndInvalidWarehousesBeforeCallingService() throws Exception {
        var service=mock(OrderStockReservationService.class);
        var mvc=MockMvcBuilders.standaloneSetup(new OrderStockReservationController(service,mock(com.aquasuite.ims.module.sales.internal.service.ReservationAllocationRunner.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        String path="/api/v1/sales/orders/"+UUID.randomUUID()+"/stock-reservations/requests";
        for(String body : new String[]{"{}",
                "{\"requestId\":\""+UUID.randomUUID()+"\",\"warehouseCode\":\"../WH\",\"lines\":[]}",
                "{\"requestId\":\""+UUID.randomUUID()+"\",\"warehouseCode\":\"WH_MAIN\",\"lines\":[{\"orderItemId\":\""+UUID.randomUUID()+"\",\"quantity\":0}]}"}) {
            mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("SYS_003"));
        }
        verifyNoInteractions(service);
    }
    @Test void accessDeniedUsesTheStandardEnvelope() throws Exception {
        var service=mock(OrderStockReservationService.class);UUID order=UUID.randomUUID();
        when(service.get(order)).thenThrow(new AppException(CommonErrorCode.ACCESS_DENIED));
        MockMvcBuilders.standaloneSetup(new OrderStockReservationController(service,mock(com.aquasuite.ims.module.sales.internal.service.ReservationAllocationRunner.class)))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/api/v1/sales/orders/"+order+"/stock-reservations"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_002"));
    }
}
