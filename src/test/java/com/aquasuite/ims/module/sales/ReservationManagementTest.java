package com.aquasuite.ims.module.sales;

import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.inventory.api.StockReservationApi;
import com.aquasuite.ims.module.inventory.api.StockReservationApi.*;
import com.aquasuite.ims.module.sales.internal.controller.ReservationManagementController;
import com.aquasuite.ims.module.sales.internal.entity.*;
import com.aquasuite.ims.module.sales.internal.enums.*;
import com.aquasuite.ims.module.sales.internal.repository.*;
import com.aquasuite.ims.module.sales.internal.service.ReservationManagementService;
import com.aquasuite.ims.share.exception.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static com.aquasuite.ims.module.sales.internal.error.ReservationManagementErrorCode.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ReservationManagementTest {
    OrderRepository orders = mock(OrderRepository.class);
    OrderModificationRequestRepository decisions = mock(OrderModificationRequestRepository.class);
    OrderModificationRequestItemRepository items = mock(OrderModificationRequestItemRepository.class);
    ReservationReleaseStore releases = mock(ReservationReleaseStore.class);
    StockReservationApi inventory = mock(StockReservationApi.class);
    IamApi iam = mock(IamApi.class);
    ReservationManagementService service = new ReservationManagementService(orders, decisions, items, releases, inventory, iam);
    UUID order = UUID.randomUUID(), item = UUID.randomUUID(), variant = UUID.randomUUID(),
            reservation = UUID.randomUUID(), decisionId = UUID.randomUUID(), owner = UUID.randomUUID();
    OrderModificationRequest decision;
    MockMvc mvc;

    @BeforeEach void setup() {
        var user = UserSummaryDto.builder().id(owner).roles(List.of("SHOP_OWNER")).build();
        when(iam.getCurrentUser()).thenReturn(user);
        when(iam.getUserById(owner)).thenReturn(user);
        when(orders.findByIdForUpdate(order)).thenReturn(Optional.of(Order.builder().orderId(order).build()));
        when(orders.existsById(order)).thenReturn(true);
        decision = OrderModificationRequest.builder().modificationRequestId(decisionId).orderId(order)
                .requestType(OrderModificationRequestType.FULL_CANCELLATION)
                .requestStatus(OrderModificationRequestStatus.APPROVED).requestedBy(UUID.randomUUID())
                .reviewedBy(owner).reviewedAt(OffsetDateTime.now()).customerReason("Customer cancellation")
                .createdAt(OffsetDateTime.now()).decisionNote("Approved").build();
        when(decisions.findByIdForUpdate(decisionId)).thenReturn(Optional.of(decision));
        when(decisions.findByOrderIdOrderByCreatedAtAsc(order)).thenReturn(List.of(decision));
        when(inventory.findByOrder(order)).thenReturn(List.of(stock("5", "0")));
        mvc = MockMvcBuilders.standaloneSetup(new ReservationManagementController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    Reservation stock(String held, String consumed) {
        return new Reservation(reservation, order, item, variant, "WH_MAIN", new BigDecimal("8"),
                new BigDecimal(held), new BigDecimal(consumed), BigDecimal.ZERO, new BigDecimal("3"));
    }
    void reduce(String remaining) {
        decision.setRequestType(OrderModificationRequestType.ITEM_CHANGE);
        when(items.findByModificationRequestIdOrderByCreatedAtAsc(decisionId)).thenReturn(List.of(
                OrderModificationRequestItem.builder().orderItemId(item).originalVariantId(variant)
                        .originalQuantity(new BigDecimal("8")).requestedQuantity(new BigDecimal(remaining))
                        .changeAction(OrderModificationItemAction.REDUCE).build()));
    }
    void error(Runnable task, BaseErrorCode code) {
        assertThatThrownBy(task::run).isInstanceOfSatisfying(AppException.class,
                e -> assertThat(e.getErrorCode()).isEqualTo(code));
    }

    @Test void fullReleaseUsesServerQuantityActorAndStableKey() {
        var result = service.release(order, decisionId);
        assertThat(result.lines().getFirst().releasedQty()).isEqualByComparingTo("5");
        verify(inventory).release(argThat(c -> c.reservationId().equals(reservation)
                && c.quantity().compareTo(new BigDecimal("5")) == 0 && c.actorId().equals(owner)
                && c.reason().contains(decisionId.toString())));
        verify(releases).save(result);
        when(releases.find(decisionId)).thenReturn(Optional.of(result));
        assertThat(service.release(order, decisionId)).isEqualTo(result);
        verify(inventory, times(1)).release(any());
    }
    @Test void partialReductionKeepsRetainedQuantityAndNeverReleasesWaitingUnits() {
        reduce("2");
        assertThat(service.release(order, decisionId).lines().getFirst().releasedQty()).isEqualByComparingTo("3");
    }
    @Test void reductionCoveredByWaitingUnitsPersistsZeroResult() {
        reduce("6");
        var result = service.release(order, decisionId);
        assertThat(result.lines().getFirst().releasedQty()).isZero();
        verify(inventory, never()).release(any());
        verify(releases).save(result);
    }
    @Test void pendingRejectedAndPendingCancelCannotRelease() {
        for (var status : List.of(OrderModificationRequestStatus.PENDING_REVIEW,
                OrderModificationRequestStatus.REJECTED, OrderModificationRequestStatus.PENDING_CANCEL)) {
            decision.setRequestStatus(status);
            error(() -> service.release(order, decisionId), INVALID_APPROVAL);
        }
        verify(inventory, never()).release(any());
    }
    @Test void wrongOrderMissingReviewerOrNonOwnerApprovalCannotRelease() {
        decision.setOrderId(UUID.randomUUID());
        error(() -> service.release(order, decisionId), INVALID_APPROVAL);
        decision.setOrderId(order);
        decision.setReviewedBy(null);
        error(() -> service.release(order, decisionId), INVALID_APPROVAL);
        decision.setReviewedBy(owner);
        when(iam.getUserById(owner)).thenReturn(UserSummaryDto.builder().id(owner).roles(List.of("ACCOUNTANT")).build());
        error(() -> service.release(order, decisionId), INVALID_APPROVAL);
        verify(inventory, never()).release(any());
    }
    @Test void swapsAndConsumedStockRequireReconciliation() {
        reduce("2");
        var line = items.findByModificationRequestIdOrderByCreatedAtAsc(decisionId).getFirst();
        line.setChangeAction(OrderModificationItemAction.SWAP);
        error(() -> service.release(order, decisionId), REVIEW_REQUIRED);
        line.setChangeAction(OrderModificationItemAction.REDUCE);
        when(inventory.findByOrder(order)).thenReturn(List.of(stock("5", "1")));
        error(() -> service.release(order, decisionId), REVIEW_REQUIRED);
        verify(releases, never()).save(any());
    }
    @Test void everyStaffRoleCanReadOtherOrdersWithoutFinanceFields() throws Exception {
        for (String role : List.of("SALES_STAFF", "ACCOUNTANT", "SHOP_OWNER")) {
            when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(UUID.randomUUID()).roles(List.of(role)).build());
            mvc.perform(get(path() + "/history")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.decisions[0].requestedBy").value(decision.getRequestedBy().toString()))
                    .andExpect(jsonPath("$.data.decisions[0].reviewedBy").value(owner.toString()))
                    .andExpect(jsonPath("$.data.payment").doesNotExist());
            String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(service.history(order));
            assertThat(json).doesNotContain("priceDifference", "depositRequired", "verifiedPaid", "unitPrice", "invoice");
        }
    }
    @Test void historyResolvesOnlyReferencedActorsInOneBatch() throws Exception {
        UUID creator = decision.getRequestedBy(), executor = UUID.randomUUID(), unrelated = UUID.randomUUID();
        when(inventory.history(reservation)).thenReturn(List.of(new Activity(UUID.randomUUID(), reservation,
                "RESERVE", BigDecimal.ZERO, BigDecimal.ONE, creator, null, Instant.now())));
        when(releases.find(decisionId)).thenReturn(Optional.of(
                new com.aquasuite.ims.module.sales.internal.payload.response.ReservationManagementView.ReleaseResult(
                        decisionId, order, executor, Instant.now(), List.of())));
        var names = Map.of(creator, "Creator name", owner, "Reviewer name", executor, "Executor name");
        when(iam.findUserDisplayNames(Set.of(creator, owner, executor))).thenReturn(names);
        var result = service.history(order);
        assertThat(result.actorNames()).containsAllEntriesOf(names).doesNotContainKey(unrelated);
        verify(iam, times(1)).findUserDisplayNames(Set.of(creator, owner, executor));
        mvc.perform(get(path() + "/history")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.actorNames['" + creator + "']").value("Creator name"))
                .andExpect(jsonPath("$.data.activities[0].actorId").value(creator.toString()));
    }
    @Test void customerCannotReadAndSalesOrAccountantCannotExecute() throws Exception {
        when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(owner).roles(List.of("CUSTOMER")).build());
        mvc.perform(get(path() + "/history")).andExpect(status().isForbidden());
        for (String role : List.of("SALES_STAFF", "ACCOUNTANT", "ADMIN")) {
            when(iam.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(owner).roles(List.of(role)).build());
            mvc.perform(post(path() + "/releases/" + decisionId)).andExpect(status().isForbidden());
        }
        verify(inventory, never()).release(any());
    }
    @Test void releaseEndpointNeedsNoQuantityOrWarehousePayload() throws Exception {
        mvc.perform(post(path() + "/releases/" + decisionId)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lines[0].releasedQty").value(5));
    }
    String path() { return "/api/v1/sales/orders/" + order + "/stock-reservations"; }
}
