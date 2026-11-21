package com.aquasuite.ims.controller.sales;

import com.aquasuite.ims.dto.sales.response.SalesOrderPageResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderSummaryResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SalesOrderPageResponseTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void preservesExistingJsonForEmptyAndSortedNonFirstPage(boolean empty) throws Exception {
        var mapper = new ObjectMapper().findAndRegisterModules();
        var content = empty ? List.<SalesOrderSummaryResponse>of()
                : List.of(SalesOrderSummaryResponse.builder().orderCode("SO-1").build());
        var pageable = empty ? PageRequest.of(0,20) : PageRequest.of(1,20, Sort.by("orderDate").descending());
        var page = new PageImpl<>(content,pageable,empty ? 0 : 41);
        var legacy = mapper.readTree(mapper.writeValueAsString(page));
        var stable = mapper.readTree(mapper.writeValueAsString(SalesOrderPageResponse.from(page)));
        assertThat(stable).isEqualTo(legacy);

        var service = org.mockito.Mockito.mock(com.aquasuite.ims.service.sales.SalesOrderService.class);
        var filter = com.aquasuite.ims.dto.sales.request.SalesOrderFilterRequest.builder()
                .depositReservationOnly(true).build();
        org.mockito.Mockito.when(service.getSalesOrders(filter,pageable)).thenReturn(page);
        var response = new SalesOrderController(service).listOrders(filter,pageable).getBody();
        assertThat(response.getData()).isEqualTo(SalesOrderPageResponse.from(page));
        org.mockito.Mockito.verify(service).getSalesOrders(filter,pageable);
    }
}
