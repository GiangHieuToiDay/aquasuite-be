package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.finance.response.OrderReceivableView;
import com.aquasuite.ims.dto.sales.request.OrderStockReservationRequest.Line;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.entity.sales.OrderItem;
import com.aquasuite.ims.exception.AppException;
import com.aquasuite.ims.repository.sales.*;
import com.aquasuite.ims.repository.sales.store.OrderStockReservationStore;
import com.aquasuite.ims.repository.sales.store.ReservationReleaseStore;
import com.aquasuite.ims.service.finance.PaymentService;
import com.aquasuite.ims.service.iam.IamQueryService;
import com.aquasuite.ims.service.inventory.StockReservationService;
import com.aquasuite.ims.service.sales.helper.ReservationActorNames;
import com.aquasuite.ims.service.sales.impl.OrderReservationServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.aquasuite.ims.exception.sales.ProjectDepositErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OrderReservationEffectiveLinesTest {
    private final OrderRepository orders = mock(OrderRepository.class);
    private final OrderItemRepository items = mock(OrderItemRepository.class);
    private final OrderStockReservationStore store = mock(OrderStockReservationStore.class);
    private final PaymentService finance = mock(PaymentService.class);
    private final StockReservationService inventory = mock(StockReservationService.class);
    private final IamQueryService iam = mock(IamQueryService.class);
    private final ObjectMapper json = new ObjectMapper();
    private final UUID orderId = UUID.randomUUID(), actorId = UUID.randomUUID();
    private final OrderReservationServiceImpl service = new OrderReservationServiceImpl(orders, items,
            store, finance, inventory, iam, json, mock(OrderModificationRequestRepository.class),
            mock(OrderModificationRequestItemRepository.class), mock(ReservationReleaseStore.class),
            new ReservationActorNames(iam));
    private OrderItem active;

    @BeforeEach void setup() throws Exception {
        when(orders.findById(orderId)).thenReturn(Optional.of(Order.builder().orderId(orderId)
                .orderStatus("PROCESSING").customerId(UUID.randomUUID()).totalAmount(new BigDecimal("100")).build()));
        active = line("2", "100");
        select("2");
    }

    private OrderItem line(String quantity, String total) {
        return OrderItem.builder().orderItemId(UUID.randomUUID()).variantId(UUID.randomUUID())
                .quantity(new BigDecimal(quantity)).lineTotal(new BigDecimal(total)).build();
    }

    private void select(String quantity) throws Exception {
        BigDecimal qty = new BigDecimal(quantity).stripTrailingZeros();
        String pricing = json.writeValueAsString(List.of(new BigDecimal("100").stripTrailingZeros(),
                List.of(List.of(active.getOrderItemId(), active.getVariantId(), qty,
                        active.getLineTotal().stripTrailingZeros()))));
        when(store.selection(orderId)).thenReturn(Optional.of(new OrderStockReservationStore.Selection(
                json.writeValueAsString(List.of(new Line(active.getOrderItemId(), qty))), pricing,
                new BigDecimal("100"), "WH_MAIN", actorId)));
    }

    @Test void removedHistoricalRowDoesNotInvalidateUnchangedActiveSnapshot() {
        when(items.findByOrderId(orderId)).thenReturn(List.of(line("0", "0"), active));
        when(finance.getOrderReceivable(orderId)).thenReturn(Optional.of(new OrderReceivableView(orderId,
                new BigDecimal("100"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100"),
                "UNPAID", new BigDecimal("20"), false)));

        assertThatCode(() -> service.allocateAutomatically(orderId, UUID.randomUUID())).doesNotThrowAnyException();
        verify(finance).getOrderReceivable(orderId);
        verifyNoInteractions(inventory);
    }

    @Test void changedActiveQuantityStillRequiresReconciliation() {
        active.setQuantity(new BigDecimal("3"));
        when(items.findByOrderId(orderId)).thenReturn(List.of(line("0", "0"), active));
        error(RESERVATION_SELECTION_CONFLICT);
        verifyNoInteractions(finance, inventory);
    }

    @Test void historicalRowDoesNotHideInvalidNegativeQuantity() {
        when(items.findByOrderId(orderId)).thenReturn(List.of(active, line("-1", "0")));
        error(ORDER_NOT_ELIGIBLE);
        verifyNoInteractions(finance, inventory);
    }

    @Test void noActiveLinesCannotBeAllocated() {
        when(items.findByOrderId(orderId)).thenReturn(List.of(line("0", "0")));
        error(ORDER_NOT_ELIGIBLE);
        verifyNoInteractions(finance, inventory);
    }

    private void error(com.aquasuite.ims.exception.sales.ProjectDepositErrorCode code) {
        assertThatThrownBy(() -> service.allocateAutomatically(orderId, UUID.randomUUID()))
                .isInstanceOfSatisfying(AppException.class, e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }
}
