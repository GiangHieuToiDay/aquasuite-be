package com.aquasuite.ims.controller.inventory;

import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.dto.iam.response.UserSummaryDto;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.dto.inventory.response.SkuReservationStock;
import com.aquasuite.ims.controller.inventory.InventoryController;
import com.aquasuite.ims.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class StockReservationQueryHttpTest {
    @Test void showsThreeHeldAndSevenAvailableForTheSku() throws Exception {
        var inventory=mock(StockReservationService.class);
        var iam=mock(IamQueryService.class);
        UUID variant=UUID.randomUUID();
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(UUID.randomUUID()).roles(List.of("SALES_STAFF")).build());
        when(inventory.stockByVariant(variant)).thenReturn(List.of(new SkuReservationStock(variant,"WH_MAIN",
                BigDecimal.TEN,new BigDecimal("3"),new BigDecimal("7"))));
        MockMvcBuilders.standaloneSetup(new InventoryController(null, inventory, iam))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/api/v1/inventory/stock-reservations/variants/"+variant))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].quantities[0].status").value("HELD"))
                .andExpect(jsonPath("$.data[0].quantities[0].quantity").value(3))
                .andExpect(jsonPath("$.data[0].quantities[1].status").value("AVAILABLE"))
                .andExpect(jsonPath("$.data[0].quantities[1].quantity").value(7));
    }
    @Test void customerCannotReadStaffStockReservationView() throws Exception {
        var inventory=mock(StockReservationService.class);var iam=mock(IamQueryService.class);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(UUID.randomUUID()).roles(List.of("CUSTOMER")).build());
        MockMvcBuilders.standaloneSetup(new InventoryController(null, inventory, iam))
                .setControllerAdvice(new GlobalExceptionHandler()).build()
                .perform(get("/api/v1/inventory/stock-reservations/variants/"+UUID.randomUUID()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_002"));
        verifyNoInteractions(inventory);
    }
}
