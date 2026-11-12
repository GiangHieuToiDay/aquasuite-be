package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi;
import com.aquasuite.ims.module.finance.api.ProjectDepositPaymentApi.*;
import com.aquasuite.ims.module.finance.internal.dev.DemoProjectDepositPaymentApi;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.internal.dev.DemoProjectReservationOrderFactory;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.module.catalog.api.dto.CatalogSkuSummaryDto;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerSummaryDto;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.ProjectReservationApi;
import com.aquasuite.ims.module.inventory.api.ProjectReservationApi.*;
import com.aquasuite.ims.module.inventory.internal.dev.DemoProjectReservationApi;
import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.payload.request.ProjectDepositRequests.*;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.module.sales.internal.service.ProjectDepositService;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProjectDepositServiceTest {
    private final UUID orderId = UUID.randomUUID(), lineId = UUID.randomUUID(), variantId = UUID.randomUUID(), actor = UUID.randomUUID();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-31T10:00:00Z"), ZoneOffset.UTC);
    private ProjectDepositOrderRepository orders;
    private ProjectDepositOrderItemRepository items;
    private DemoProjectDepositPaymentApi finance;
    private DemoProjectReservationApi inventory;
    private ProjectDepositService service;
    private IamApi iam;
    private Order order;
    private DemoProjectReservationOrderFactory factory;

    @BeforeEach
    void setup() {
        orders = mock(ProjectDepositOrderRepository.class);
        items = mock(ProjectDepositOrderItemRepository.class);
        iam = mock(IamApi.class);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SALES_STAFF")).build());
        when(iam.getCurrentUserId()).thenReturn(actor);
        order = Order.builder().orderId(orderId).customerId(UUID.randomUUID())
                .salesStaffId(actor).orderStatus("CONFIRMED").totalAmount(new BigDecimal("100.00")).build();
        when(orders.findById(orderId)).thenReturn(Optional.of(order));
        when(items.findByOrderIdOrderByOrderItemId(orderId)).thenReturn(List.of(OrderItem.builder()
                .orderItemId(lineId).orderId(orderId).variantId(variantId).quantity(new BigDecimal("10")).lineTotal(new BigDecimal("100")).build()));
        finance = new DemoProjectDepositPaymentApi();
        inventory = new DemoProjectReservationApi(clock);
        inventory.setStock("WH_MAIN", variantId, new BigDecimal("6"));
        factory = mock(DemoProjectReservationOrderFactory.class);
        service = makeService(finance, inventory);
        service.requestReservation(orderId, selection("10"));
        clearInvocations(orders, items);
    }

    private ProjectDepositService makeService(ProjectDepositPaymentApi f, ProjectReservationApi i) {
        return new ProjectDepositService(orders, items, f, i, iam, clock, factory);
    }
    private Selection selection(String quantity) {
        return new Selection(UUID.randomUUID(), "WH_MAIN", List.of(new RequestedLine(lineId, new BigDecimal(quantity))));
    }
    private Deposit deposit(UUID id, String amount) { return new Deposit(id, new BigDecimal(amount), Method.CASH, null, null, "WH_MAIN"); }

    @Test
    void pendingPaymentDoesNotPromoteAndVerifiedDepositReusesHoldWithoutIncreasingStock() {
        inventory.allocate(new Allocation(UUID.randomUUID(), orderId, "WH_MAIN", HoldType.TEMPORARY,
                clock.instant().plus(Duration.ofHours(48)), List.of(new Line(lineId, variantId, BigDecimal.TEN)), actor));
        var temporary = service.get(orderId);
        UUID reservationId = temporary.reservations().getFirst().reservationId();
        assertThat(temporary.lines().getFirst().heldQty()).isEqualByComparingTo("6");
        assertThat(temporary.lines().getFirst().waitingQty()).isEqualByComparingTo("4");
        UUID key = UUID.randomUUID();
        var pending = service.deposit(orderId, deposit(key, "30.00"));
        assertThat(pending.verifiedPaidAmount()).isZero();
        assertThat(pending.reservations().getFirst().type()).isEqualTo(HoldType.TEMPORARY);
        finance.verify(pending.payments().getFirst().paymentId(), Status.VERIFIED);
        var promoted = service.deposit(orderId, deposit(key, "30.00"));
        assertThat(promoted.simulated()).isTrue();
        assertThat(promoted.depositSatisfied()).isTrue();
        assertThat(promoted.outstandingAmount()).isEqualByComparingTo("70");
        assertThat(promoted.reservations()).hasSize(1);
        assertThat(promoted.reservations().getFirst().reservationId()).isEqualTo(reservationId);
        assertThat(promoted.reservations().getFirst().expiresAt()).isNull();
        assertThat(promoted.reservations().getFirst().heldQty()).isEqualByComparingTo("6");
        assertThat(promoted.reservationHistory()).anyMatch(h -> h.action().equals("PROMOTED") && h.heldBefore().compareTo(h.heldAfter()) == 0);
        assertThat(inventory.setStock("WH_MAIN", variantId, new BigDecimal("6")).onHandQty()).isEqualByComparingTo("6");
        service.deposit(orderId, deposit(key, "30.00"));
        service.synchronizeReservations(orderId, new Hold(UUID.randomUUID(), "WH_MAIN"));
        assertThat(finance.getPayments(orderId)).hasSize(1);
        assertThat(service.get(orderId).lines().getFirst().heldQty()).isEqualByComparingTo("6");
        verify(orders, never()).save(any());
        verify(items, never()).save(any());
    }

    @Test
    void sameKeyWithDifferentInputIsConflictAndPendingAmountsPreventOvercollection() {
        UUID key = UUID.randomUUID();
        service.deposit(orderId, deposit(key, "30"));
        assertThatThrownBy(() -> service.deposit(orderId, deposit(key, "31")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException)e).getErrorCode().getCode()).isEqualTo("SALES_DEPOSIT_004");
        assertThatThrownBy(() -> service.deposit(orderId, deposit(UUID.randomUUID(), "80"))).isInstanceOf(AppException.class);
        assertThat(finance.getPayments(orderId)).hasSize(1);
    }

    @Test
    void underpaymentRemainsTemporaryAndRejectedPaymentsNeverCount() {

        var pending = service.deposit(orderId, deposit(UUID.randomUUID(), "10"));
        finance.verify(pending.payments().getFirst().paymentId(), Status.VERIFIED);
        var result = service.synchronizeReservations(orderId, new Hold(UUID.randomUUID(), "WH_MAIN"));
        assertThat(result.depositSatisfied()).isFalse();
        assertThat(result.reservationSyncStatus()).isEqualTo("BELOW_REQUIRED_DEPOSIT");
        assertThat(result.reservations()).isEmpty();
        var second = service.deposit(orderId, deposit(UUID.randomUUID(), "20"));
        finance.verify(second.payments().getLast().paymentId(), Status.REJECTED);
        assertThat(service.get(orderId).verifiedPaidAmount()).isEqualByComparingTo("10");
    }

    @Test
    void inventoryFailureCanBeRetriedWithoutRecordingPaymentAgain() {
        ProjectReservationApi flaky = spy(inventory);
        ProjectDepositService target = makeService(finance, flaky);
        target.requestReservation(orderId, selection("10"));
        UUID key = UUID.randomUUID();
        var pending = target.deposit(orderId, deposit(key, "30"));
        finance.verify(pending.payments().getFirst().paymentId(), Status.VERIFIED);
        doThrow(new IllegalStateException("simulated outage")).doCallRealMethod().when(flaky).allocate(any());
        var failed = target.deposit(orderId, deposit(key, "30"));
        assertThat(failed.reservationSyncStatus()).isEqualTo("RESERVATION_PENDING");
        assertThat(failed.verifiedPaidAmount()).isEqualByComparingTo("30");
        assertThat(target.deposit(orderId, deposit(key, "30")).lines().getFirst().heldQty()).isEqualByComparingTo("6");
        assertThat(finance.getPayments(orderId)).hasSize(1);
    }

    @Test
    void paymentResponseLossRetriesSameExternalRequestRatherThanCreatingSecondPayment() {
        ProjectDepositPaymentApi flaky = spy(finance);
        doAnswer(call -> { finance.recordDeposit(call.getArgument(0)); throw new IllegalStateException("lost response"); })
                .doAnswer(call -> finance.recordDeposit(call.getArgument(0))).when(flaky).recordDeposit(any());
        ProjectDepositService target = makeService(flaky, inventory);
        target.requestReservation(orderId, selection("10"));
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> target.deposit(orderId, deposit(key, "30"))).isInstanceOf(AppException.class);
        target.deposit(orderId, deposit(key, "30"));
        assertThat(finance.getPayments(orderId)).hasSize(1);
    }

    @Test
    void scheduleHasVersionAndDoesNotChangeMoneyOrReservations() {
        var first = service.updateSchedule(orderId, new Schedule(0, List.of(new DeliveryDate(LocalDate.of(2026,11,10), "phase 1"),
                new DeliveryDate(LocalDate.of(2026,12,10), "phase 2"))));
        assertThat(first.scheduleVersion()).isEqualTo(1);
        assertThat(first.deliveries()).hasSize(2);
        assertThat(first.reservations()).isEmpty();
        assertThatThrownBy(() -> service.updateSchedule(orderId, new Schedule(0, List.of()))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.updateSchedule(orderId, new Schedule(1, List.of(new DeliveryDate(LocalDate.of(2026,10,30), null)))))
                .isInstanceOf(AppException.class);
        assertThat(first.payments()).isEmpty();
    }

    @Test
    void authorizationAndOrderStatusAreCheckedBeforeAnyPaymentOrReservation() {
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("CUSTOMER")).build());
        assertThatThrownBy(() -> service.get(orderId)).isInstanceOf(AppException.class);
        verifyNoInteractions(orders);
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SALES_STAFF")).build());
        order.setSalesStaffId(UUID.randomUUID());
        assertThatThrownBy(() -> service.deposit(orderId, deposit(UUID.randomUUID(), "30"))).isInstanceOf(AppException.class);
        order.setSalesStaffId(actor);
        order.setOrderStatus("CANCELLED");
        assertThatThrownBy(() -> service.requestReservation(orderId, selection("10"))).isInstanceOf(AppException.class);
        assertThat(finance.getPayments(orderId)).isEmpty();
    }

    @Test
    void shopOwnerCanReviewAnOrderAssignedToAnotherSalesStaff() {
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(actor).roles(List.of("SHOP_OWNER")).build());
        order.setSalesStaffId(UUID.randomUUID());
        assertThat(service.get(orderId).orderId()).isEqualTo(orderId);
    }

    @Test
    void lostLargePaymentResponseDoesNotCountItsOwnPendingPaymentAgainstRetry() {
        ProjectDepositPaymentApi flaky = spy(finance);
        doAnswer(call -> { finance.recordDeposit(call.getArgument(0)); throw new IllegalStateException("lost response"); })
                .doAnswer(call -> finance.recordDeposit(call.getArgument(0))).when(flaky).recordDeposit(any());
        ProjectDepositService target = makeService(flaky, inventory);
        target.requestReservation(orderId, selection("10"));
        UUID key = UUID.randomUUID();
        assertThatThrownBy(() -> target.deposit(orderId, deposit(key, "80"))).isInstanceOf(AppException.class);
        assertThat(target.deposit(orderId, deposit(key, "80")).payments()).hasSize(1);
    }

    @Test
    void equivalentAmountScalesReplayTheSamePayment() {
        UUID key = UUID.randomUUID();
        service.deposit(orderId, deposit(key, "30.0"));
        assertThat(service.deposit(orderId, deposit(key, "30.00")).payments()).hasSize(1);
    }

    @Test
    void uncertainPaymentReservesItsBudgetUntilTheSameRequestIsRecovered() {
        ProjectDepositPaymentApi flaky = spy(finance);
        doThrow(new IllegalStateException("outage before acknowledgement")).doCallRealMethod().when(flaky).recordDeposit(any());
        ProjectDepositService target = makeService(flaky, inventory);
        target.requestReservation(orderId, selection("10"));
        UUID firstKey = UUID.randomUUID();
        assertThatThrownBy(() -> target.deposit(orderId, deposit(firstKey, "80"))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> target.deposit(orderId, deposit(UUID.randomUUID(), "30"))).isInstanceOf(AppException.class);
        assertThat(target.deposit(orderId, deposit(firstKey, "80")).payments()).hasSize(1);
    }

    @Test
    void ordinaryOrdersHaveNoDepositRequirementUntilCustomerRequestsHolding() {
        service = makeService(finance, inventory);
        var normal = service.get(orderId);
        assertThat(normal.reservationRequested()).isFalse();
        assertThat(normal.requiredDeposit()).isZero();
        assertThat(normal.depositSatisfied()).isFalse();
        assertThat(normal.reservations()).isEmpty();
        assertThatThrownBy(() -> service.deposit(orderId, deposit(UUID.randomUUID(), "20")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException)e).getErrorCode().getCode()).isEqualTo("SALES_DEPOSIT_007");
        assertThat(finance.getPayments(orderId)).isEmpty();
    }

    @Test
    void partialSelectionUsesTwentyPercentOfSelectedQuantityAndOnlyAllocatesThatQuantity() {
        service = makeService(finance, inventory);
        var requested = service.requestReservation(orderId, selection("4"));
        assertThat(requested.reservationValue()).isEqualByComparingTo("40");
        assertThat(requested.minimumDepositPercent()).isEqualByComparingTo("20");
        assertThat(requested.requiredDeposit()).isEqualByComparingTo("8");
        assertThat(requested.reservations()).isEmpty();
        var pending = service.deposit(orderId, deposit(UUID.randomUUID(), "7.99"));
        finance.verify(pending.payments().getFirst().paymentId(), Status.VERIFIED);
        assertThat(service.synchronizeReservations(orderId, new Hold(UUID.randomUUID(), "WH_MAIN")).reservations()).isEmpty();
        var remainder = service.deposit(orderId, deposit(UUID.randomUUID(), "0.01"));
        finance.verify(remainder.payments().getLast().paymentId(), Status.VERIFIED);
        var result = service.synchronizeReservations(orderId, new Hold(UUID.randomUUID(), "WH_MAIN"));
        assertThat(result.depositSatisfied()).isTrue();
        assertThat(result.lines().getFirst().requestedQty()).isEqualByComparingTo("4");
        assertThat(result.lines().getFirst().heldQty()).isEqualByComparingTo("4");
        assertThat(result.lines().getFirst().waitingQty()).isZero();
        assertThat(result.outstandingAmount()).isEqualByComparingTo("92");
        assertThat(inventory.setStock("WH_MAIN", variantId, new BigDecimal("6")).availableQty()).isEqualByComparingTo("2");
    }

    @Test
    void fullSelectionUsesOrderTotalAndShortageDoesNotReduceRequiredDeposit() {
        var requested = service.get(orderId);
        assertThat(requested.reservationValue()).isEqualByComparingTo("100");
        assertThat(requested.requiredDeposit()).isEqualByComparingTo("20");
        var pending = service.deposit(orderId, deposit(UUID.randomUUID(), "20"));
        finance.verify(pending.payments().getFirst().paymentId(), Status.VERIFIED);
        var result = service.synchronizeReservations(orderId, new Hold(UUID.randomUUID(), "WH_MAIN"));
        assertThat(result.lines().getFirst().heldQty()).isEqualByComparingTo("6");
        assertThat(result.lines().getFirst().waitingQty()).isEqualByComparingTo("4");
        assertThat(result.requiredDeposit()).isEqualByComparingTo("20");
    }

    @Test
    void selectionsAreServerPricedAndCannotContainOtherOrderLinesOrExcessQuantities() {
        service = makeService(finance, inventory);
        assertThatThrownBy(() -> service.requestReservation(orderId, selection("11"))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.requestReservation(orderId, new Selection(UUID.randomUUID(), "WH_MAIN",
                List.of(new RequestedLine(UUID.randomUUID(), BigDecimal.ONE))))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.requestReservation(orderId, new Selection(UUID.randomUUID(), "WH_MAIN",
                List.of(new RequestedLine(lineId, BigDecimal.ONE), new RequestedLine(lineId, BigDecimal.ONE)))))
                .isInstanceOf(AppException.class);
        service.requestReservation(orderId, selection("4"));
        assertThatThrownBy(() -> service.requestReservation(orderId, selection("5"))).isInstanceOf(AppException.class);
    }

    @Test
    void orderLevelDiscountIsAllocatedProportionallyAndPriceChangesRequireReview() {
        service = makeService(finance, inventory);
        order.setTotalAmount(new BigDecimal("80"));
        assertThat(service.requestReservation(orderId, selection("4")).reservationValue()).isEqualByComparingTo("32");
        assertThat(service.get(orderId).requiredDeposit()).isEqualByComparingTo("6.40");
        order.setTotalAmount(new BigDecimal("100"));
        assertThatThrownBy(() -> service.deposit(orderId, deposit(UUID.randomUUID(), "8"))).isInstanceOf(AppException.class);
        assertThat(finance.getPayments(orderId)).isEmpty();
    }

    @Test
    void customerWithoutOrderCreatesOneOrderAndPendingDepositAndRetriesReuseBoth() {
        CatalogApi catalog = mock(CatalogApi.class);
        CrmApi crm = mock(CrmApi.class);
        UUID customer = UUID.randomUUID();
        when(crm.getCustomerById(customer)).thenReturn(Optional.of(CustomerSummaryDto.builder().customerId(customer).status("ACTIVE").build()));
        when(catalog.getSkuSummary(variantId)).thenReturn(Optional.of(CatalogSkuSummaryDto.builder().variantId(variantId)
                .sellingPrice(BigDecimal.TEN).status("ACTIVE").unit("cái").build()));
        factory = new DemoProjectReservationOrderFactory(catalog, crm, clock);
        service = makeService(finance, inventory);
        var request = new CreateOrderDeposit(UUID.randomUUID(), customer, List.of(new NewOrderLine(variantId, BigDecimal.TEN)),
                new BigDecimal("20"), Method.CASH, null, null, "WH_MAIN");
        var created = service.createAndDeposit(request);
        assertThat(factory.findOrder(created.orderId()).orElseThrow().getProjectId()).isNull();
        assertThat(created.requiredDeposit()).isEqualByComparingTo("20");
        assertThat(created.payments().getFirst().status()).isEqualTo(Status.PENDING);
        assertThat(created.reservations()).isEmpty();
        finance.verify(created.payments().getFirst().paymentId(), Status.VERIFIED);
        var retried = service.createAndDeposit(request);
        assertThat(retried.orderId()).isEqualTo(created.orderId());
        assertThat(retried.payments()).hasSize(1);
        assertThat(retried.lines().getFirst().heldQty()).isEqualByComparingTo("6");
        var changed = new CreateOrderDeposit(request.requestId(), customer, List.of(new NewOrderLine(variantId, BigDecimal.ONE)),
                BigDecimal.ONE, Method.CASH, null, null, "WH_MAIN");
        assertThatThrownBy(() -> service.createAndDeposit(changed)).isInstanceOf(AppException.class);
        verify(orders, never()).save(any());
    }

    @Test
    void replacingTheSkuAfterSelectionCannotReuseTheOriginalDepositApproval() {
        items.findByOrderIdOrderByOrderItemId(orderId).getFirst().setVariantId(UUID.randomUUID());
        assertThatThrownBy(() -> service.deposit(orderId, deposit(UUID.randomUUID(), "20")))
                .isInstanceOf(AppException.class).extracting(e -> ((AppException)e).getErrorCode().getCode()).isEqualTo("SALES_DEPOSIT_008");
        assertThat(finance.getPayments(orderId)).isEmpty();
    }
}
