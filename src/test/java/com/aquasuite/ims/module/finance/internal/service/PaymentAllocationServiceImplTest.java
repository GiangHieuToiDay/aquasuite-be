package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.dto.OrderModificationSettlementCommand;
import com.aquasuite.ims.module.finance.api.event.CustomerDebtAuditEvent;
import com.aquasuite.ims.module.finance.internal.entity.PaymentInvoiceAllocation;
import com.aquasuite.ims.module.finance.internal.entity.PaymentReceiptRefundSettlement;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationType;
import com.aquasuite.ims.module.finance.internal.error.PaymentAllocationErrorCode;
import com.aquasuite.ims.module.finance.internal.mapper.PaymentAllocationMapper;
import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.PaymentAllocationLineRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReversePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.PaymentAllocationReceiptView;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.repository.PaymentInvoiceAllocationRepository;
import com.aquasuite.ims.module.finance.internal.repository.PaymentReceiptRefundSettlementRepository;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.service.impl.PaymentAllocationServiceImpl;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.SalesReceivableApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceImplTest {
    @Mock TransactionRepository transactionRepository;
    @Mock PaymentInvoiceAllocationRepository allocationRepository;
    @Mock PaymentReceiptRefundSettlementRepository refundSettlementRepository;
    @Mock SalesOrderApi salesOrderApi;
    @Mock SalesReceivableApi salesReceivableApi;
    @Mock PaymentAllocationMapper allocationMapper;
    @Mock ApplicationEventPublisher eventPublisher;
    @InjectMocks PaymentAllocationServiceImpl service;

    UUID receiptId;
    UUID orderId;
    UUID customerId;
    UUID actorId;
    UUID storeInvoiceId;
    UUID invoiceOneId;
    UUID invoiceTwoId;
    Transaction receipt;
    SalesOrderPaymentInfoDto order;
    SalesInvoiceDebtDto invoiceOne;
    SalesInvoiceDebtDto invoiceTwo;

    @BeforeEach
    void setup() {
        receiptId = UUID.randomUUID();
        orderId = UUID.randomUUID();
        customerId = UUID.randomUUID();
        actorId = UUID.randomUUID();
        storeInvoiceId = UUID.randomUUID();
        invoiceOneId = UUID.randomUUID();
        invoiceTwoId = UUID.randomUUID();
        receipt = Transaction.builder().transactionId(receiptId).transactionCode("PAY-001")
                .transactionType("RECEIPT").status("VERIFIED").customerId(customerId).orderId(orderId)
                .invoiceId(storeInvoiceId).amount(new BigDecimal("100.00")).paymentMethod("CASH")
                .createdBy(actorId).transactionDate(OffsetDateTime.now()).build();
        order = SalesOrderPaymentInfoDto.builder().orderId(orderId).customerId(customerId).storeInvoiceId(storeInvoiceId)
                .totalAmount(new BigDecimal("200.00")).validForPayment(true).build();
        invoiceOne = invoice(invoiceOneId, "INV-001", "60.00");
        invoiceTwo = invoice(invoiceTwoId, "INV-002", "80.00");
        lenient().when(transactionRepository.findOrderIdById(receiptId)).thenReturn(Optional.of(orderId));
        lenient().when(transactionRepository.findByIdForUpdate(receiptId)).thenReturn(Optional.of(receipt));
        lenient().when(salesOrderApi.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(order));
        lenient().when(transactionRepository.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt));
        lenient().when(allocationRepository.findByTransactionIdAndAllocationRequestId(eq(receiptId), any())).thenReturn(List.of());
        lenient().when(allocationRepository.findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of());
        lenient().when(allocationRepository.findByInvoiceIdInAndAllocationStatus(any(), eq(PaymentAllocationStatus.ACTIVE)))
                .thenReturn(List.of());
        lenient().when(allocationRepository.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(allocationMapper.toResponses(any())).thenAnswer(invocation -> ((List<PaymentInvoiceAllocation>) invocation.getArgument(0))
                .stream().map(row -> PaymentAllocationLineResponse.builder().allocationId(row.getAllocationId())
                        .allocationRequestId(row.getAllocationRequestId()).transactionId(row.getTransactionId())
                        .orderId(row.getOrderId()).invoiceId(row.getInvoiceId()).allocatedAmount(row.getAllocatedAmount())
                        .allocationType(row.getAllocationType()).allocatedBy(row.getAllocatedBy()).allocatedAt(row.getAllocatedAt())
                        .reason(row.getReason()).build()).toList());
    }

    @Test
    void allocateSplitsVerifiedStoreReceiptAndWritesAuditEvent() {
        when(salesReceivableApi.findInvoicesByIds(any())).thenReturn(List.of(invoiceOne, invoiceTwo));
        CreatePaymentAllocationRequest request = request(line(invoiceOneId, "40.00"), line(invoiceTwoId, "50.00"));

        var result = service.allocate(receiptId, request, actorId);

        assertThat(result.allocatedAmount()).isEqualByComparingTo("90.00");
        assertThat(result.remainingReceiptAmount()).isEqualByComparingTo("10.00");
        assertThat(result.allocations()).hasSize(2);
        ArgumentCaptor<List<PaymentInvoiceAllocation>> saved = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).allSatisfy(row -> {
            assertThat(row.getTransactionId()).isEqualTo(receiptId);
            assertThat(row.getOrderId()).isEqualTo(orderId);
            assertThat(row.getAllocatedBy()).isEqualTo(actorId);
        });
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.argThat((CustomerDebtAuditEvent event) ->
                "PAYMENT_ALLOCATION_CREATED".equals(event.action()) && receiptId.equals(event.entityId())));
    }

    @Test
    void allocateRejectsAmountBeyondRemainingReceipt() {
        when(salesReceivableApi.findInvoicesByIds(any())).thenReturn(List.of(invoiceOne, invoiceTwo));

        assertThatThrownBy(() -> service.allocate(receiptId, request(line(invoiceOneId, "60.00"), line(invoiceTwoId, "50.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.RECEIPT_BALANCE_EXCEEDED));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsTargetFromAnotherOrder() {
        SalesInvoiceDebtDto otherOrder = new SalesInvoiceDebtDto(invoiceOneId, UUID.randomUUID(), customerId,
                "INV-OTHER", null, "MISA-OTHER", OffsetDateTime.now(), OffsetDateTime.now(), 30, null, "ISSUED", BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, "STORE");
        when(salesReceivableApi.findInvoicesByIds(any())).thenReturn(List.of(otherOrder));

        assertThatThrownBy(() -> service.allocate(receiptId, request(line(invoiceOneId, "10.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.INVALID_ALLOCATION_TARGET));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsStoreInvoiceBeforeElectronicIntegrationCompletes() {
        SalesInvoiceDebtDto notIntegrated = new SalesInvoiceDebtDto(invoiceOneId, orderId, customerId,
                "STORE-UNSIGNED", null, null, OffsetDateTime.now(), null, 30, null, "ISSUED", BigDecimal.TEN,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.TEN, null, "STORE");
        when(salesReceivableApi.findInvoicesByIds(any())).thenReturn(List.of(notIntegrated));

        assertThatThrownBy(() -> service.allocate(receiptId, request(line(invoiceOneId, "10.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.INVALID_ALLOCATION_TARGET));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsDuplicateInvoiceLineBeforeWriting() {
        assertThatThrownBy(() -> service.allocate(receiptId,
                request(line(invoiceOneId, "10.00"), line(invoiceOneId, "10.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.DUPLICATE_INVOICE_LINE));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsPendingReceipt() {
        receipt.setStatus("PENDING");

        assertThatThrownBy(() -> service.allocate(receiptId, request(line(invoiceOneId, "10.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.RECEIPT_NOT_ALLOCATABLE));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void listsOnlyVerifiedReceiptsThatStillHaveAnAllocationBalance() {
        PaymentInvoiceAllocation previousAllocation = PaymentInvoiceAllocation.builder()
                .allocatedAmount(new BigDecimal("40.00"))
                .build();
        when(transactionRepository.findVerifiedReceiptsWithRemainingAllocation(
                PaymentAllocationStatus.ACTIVE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(receipt)));
        when(allocationRepository.findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of(previousAllocation));

        var result = service.listUnallocatedReceipts(PageRequest.of(0, 20));

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.receiptId()).isEqualTo(receiptId);
            assertThat(row.receiptAmount()).isEqualByComparingTo("100.00");
            assertThat(row.allocatedAmount()).isEqualByComparingTo("40.00");
            assertThat(row.remainingAmount()).isEqualByComparingTo("60.00");
        });
    }

    @Test
    void listsFullyAllocatedVerifiedReceiptsForTheAllocatedLedgerView() {
        PaymentInvoiceAllocation previousAllocation = PaymentInvoiceAllocation.builder()
                .allocatedAmount(new BigDecimal("100.00"))
                .build();
        when(transactionRepository.findFullyAllocatedVerifiedReceipts(
                PaymentAllocationStatus.ACTIVE, PageRequest.of(0, 20)))
                .thenReturn(new PageImpl<>(List.of(receipt)));
        when(allocationRepository.findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of(previousAllocation));

        var result = service.listReceipts(PaymentAllocationReceiptView.ALLOCATED, PageRequest.of(0, 20));

        assertThat(result.getContent()).singleElement().satisfies(row -> {
            assertThat(row.receiptId()).isEqualTo(receiptId);
            assertThat(row.allocatedAmount()).isEqualByComparingTo("100.00");
            assertThat(row.remainingAmount()).isZero();
        });
        verify(transactionRepository).findFullyAllocatedVerifiedReceipts(
                PaymentAllocationStatus.ACTIVE, PageRequest.of(0, 20));
    }

    @Test
    void automaticallyAllocatesAllVerifiedMoneyForOneStageOrder() {
        when(salesReceivableApi.findElectronicallyIntegratedInvoicesByOrderId(orderId))
                .thenReturn(List.of(invoiceOne, invoiceTwo));

        service.autoAllocateVerifiedReceipt(receiptId, actorId);

        ArgumentCaptor<List<PaymentInvoiceAllocation>> saved = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).hasSize(2);
        assertThat(saved.getValue()).extracting(PaymentInvoiceAllocation::getInvoiceId)
                .containsExactly(invoiceOneId, invoiceTwoId);
        assertThat(saved.getValue()).extracting(PaymentInvoiceAllocation::getAllocatedAmount)
                .containsExactly(new BigDecimal("60.00"), new BigDecimal("40.00"));
        assertThat(saved.getValue()).allSatisfy(row ->
                assertThat(row.getAllocationType()).isEqualTo(PaymentAllocationType.AUTO_ADVANCE));
    }

    @Test
    void automaticallyAllocatesOnlyEarliestVerifiedDepositAcrossStagedOrderReceipts() {
        Transaction laterReceipt = Transaction.builder().transactionId(UUID.randomUUID()).transactionCode("PAY-002")
                .transactionType("RECEIPT").status("VERIFIED").customerId(customerId).orderId(orderId)
                .amount(new BigDecimal("100.00")).paymentMethod("BANK_TRANSFER").createdBy(actorId)
                .transactionDate(receipt.getTransactionDate().plusMinutes(1)).build();
        receipt.setAmount(new BigDecimal("30.00"));
        SalesOrderPaymentInfoDto stagedOrder = SalesOrderPaymentInfoDto.builder()
                .orderId(orderId).customerId(customerId).storeInvoiceId(storeInvoiceId)
                .totalAmount(new BigDecimal("200.00")).depositAmount(new BigDecimal("50.00"))
                .stagedDelivery(true).validForPayment(true).build();
        when(salesOrderApi.lockOrderPaymentInfo(orderId)).thenReturn(Optional.of(stagedOrder));
        when(transactionRepository.findByOrderIdAndTransactionTypeIgnoreCase(orderId, "RECEIPT"))
                .thenReturn(List.of(receipt, laterReceipt));
        when(transactionRepository.findByIdForUpdate(laterReceipt.getTransactionId())).thenReturn(Optional.of(laterReceipt));
        when(allocationRepository.findByTransactionIdAndAllocationStatus(
                laterReceipt.getTransactionId(), PaymentAllocationStatus.ACTIVE)).thenReturn(List.of());
        when(salesReceivableApi.findElectronicallyIntegratedInvoicesByOrderId(orderId)).thenReturn(List.of(invoiceOne));

        service.autoAllocateVerifiedReceipt(receiptId, actorId);

        ArgumentCaptor<List<PaymentInvoiceAllocation>> saved = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository).saveAllAndFlush(saved.capture());
        assertThat(saved.getValue()).extracting(PaymentInvoiceAllocation::getTransactionId)
                .containsExactly(receiptId, laterReceipt.getTransactionId());
        assertThat(saved.getValue()).extracting(PaymentInvoiceAllocation::getAllocatedAmount)
                .containsExactly(new BigDecimal("30.00"), new BigDecimal("20.00"));
        assertThat(saved.getValue()).allSatisfy(row -> {
            assertThat(row.getInvoiceId()).isEqualTo(invoiceOneId);
            assertThat(row.getReason()).isEqualTo("Tự động phân bổ FIFO phần tiền cọc");
        });
    }

    @Test
    void reverseKeepsReceiptImmutableAndReturnsItsMoneyToManualQueue() {
        UUID allocationId = UUID.randomUUID();
        PaymentInvoiceAllocation active = PaymentInvoiceAllocation.builder().allocationId(allocationId)
                .transactionId(receiptId).orderId(orderId).invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00")).allocationStatus(PaymentAllocationStatus.ACTIVE)
                .allocatedBy(actorId).allocatedAt(OffsetDateTime.now()).createdAt(OffsetDateTime.now()).build();
        when(allocationRepository.findByIdForUpdate(allocationId)).thenReturn(Optional.of(active));
        when(allocationRepository.findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of());
        when(allocationRepository.saveAndFlush(active)).thenReturn(active);

        service.reverse(receiptId, allocationId,
                new ReversePaymentAllocationRequest("Phân bổ nhầm hóa đơn đợt giao"), actorId);

        assertThat(active.getAllocationStatus()).isEqualTo(PaymentAllocationStatus.REVERSED);
        assertThat(active.getReversedBy()).isEqualTo(actorId);
        assertThat(active.getReversalReason()).isEqualTo("Phân bổ nhầm hóa đơn đợt giao");
        assertThat(receipt.getStatus()).isEqualTo("VERIFIED");
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.argThat((CustomerDebtAuditEvent event) ->
                "PAYMENT_ALLOCATION_REVERSED".equals(event.action()) && receiptId.equals(event.entityId())));
    }

    @Test
    void allocateReplaysIdempotentBatchWhenSameAllocationRequestIdAndPayloadAreSent() {
        UUID requestId = UUID.randomUUID();
        PaymentInvoiceAllocation existing = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .allocationRequestId(requestId)
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00"))
                .allocationType(PaymentAllocationType.MANUAL)
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .reason("Reconcile issued invoices")
                .allocatedBy(actorId)
                .allocatedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        when(allocationRepository.findByTransactionIdAndAllocationRequestId(receiptId, requestId))
                .thenReturn(List.of(existing));
        when(allocationRepository.findByTransactionIdAndAllocationStatus(receiptId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of(existing));

        CreatePaymentAllocationRequest replayRequest = CreatePaymentAllocationRequest.builder()
                .allocationRequestId(requestId)
                .allocations(List.of(line(invoiceOneId, "40.00")))
                .reason("Reconcile issued invoices")
                .build();

        var result = service.allocate(receiptId, replayRequest, actorId);

        assertThat(result.allocationRequestId()).isEqualTo(requestId);
        assertThat(result.allocatedAmount()).isEqualByComparingTo("40.00");
        assertThat(result.remainingReceiptAmount()).isEqualByComparingTo("60.00");
        assertThat(result.allocations()).hasSize(1);
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsConflictingPayloadOnExistingAllocationRequestId() {
        UUID requestId = UUID.randomUUID();
        PaymentInvoiceAllocation existing = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .allocationRequestId(requestId)
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00"))
                .allocationType(PaymentAllocationType.MANUAL)
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .reason("Reconcile issued invoices")
                .allocatedBy(actorId)
                .allocatedAt(OffsetDateTime.now())
                .createdAt(OffsetDateTime.now())
                .build();
        when(allocationRepository.findByTransactionIdAndAllocationRequestId(receiptId, requestId))
                .thenReturn(List.of(existing));

        CreatePaymentAllocationRequest conflictingRequest = CreatePaymentAllocationRequest.builder()
                .allocationRequestId(requestId)
                .allocations(List.of(line(invoiceOneId, "50.00")))
                .reason("Reconcile issued invoices")
                .build();

        assertThatThrownBy(() -> service.allocate(receiptId, conflictingRequest, actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.ALLOCATION_REQUEST_CONFLICT));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void allocateRejectsAmountBeyondInvoiceRemainingBalance() {
        PaymentInvoiceAllocation existingInvoiceAllocation = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .transactionId(UUID.randomUUID())
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("30.00"))
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .build();
        when(salesReceivableApi.findInvoicesByIds(any())).thenReturn(List.of(invoiceOne));
        when(allocationRepository.findByInvoiceIdInAndAllocationStatus(any(), eq(PaymentAllocationStatus.ACTIVE)))
                .thenReturn(List.of(existingInvoiceAllocation));

        assertThatThrownBy(() -> service.allocate(receiptId, request(line(invoiceOneId, "40.00")), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.INVOICE_BALANCE_EXCEEDED));
        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void reverseRejectsAllocationThatIsAlreadyReversed() {
        UUID allocationId = UUID.randomUUID();
        PaymentInvoiceAllocation alreadyReversed = PaymentInvoiceAllocation.builder()
                .allocationId(allocationId)
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00"))
                .allocationType(PaymentAllocationType.MANUAL)
                .allocationStatus(PaymentAllocationStatus.REVERSED)
                .build();
        when(allocationRepository.findByIdForUpdate(allocationId)).thenReturn(Optional.of(alreadyReversed));

        assertThatThrownBy(() -> service.reverse(
                receiptId, allocationId, new ReversePaymentAllocationRequest("Thu hồi lần hai"), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.ALLOCATION_ALREADY_REVERSED));
        verify(allocationRepository, never()).saveAndFlush(any());
    }

    @Test
    void reverseRejectsRepointedAllocation() {
        UUID allocationId = UUID.randomUUID();
        PaymentInvoiceAllocation repointed = PaymentInvoiceAllocation.builder()
                .allocationId(allocationId)
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00"))
                .allocationType(PaymentAllocationType.REPOINTED)
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .build();
        when(allocationRepository.findByIdForUpdate(allocationId)).thenReturn(Optional.of(repointed));

        assertThatThrownBy(() -> service.reverse(
                receiptId, allocationId, new ReversePaymentAllocationRequest("Thử thu hồi dòng điều chuyển"), actorId))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.ALLOCATION_NOT_REVERSIBLE));
        verify(allocationRepository, never()).saveAndFlush(any());
    }

    @Test
    void autoAllocateSkipsReceiptAfterManualReversal() {
        PaymentInvoiceAllocation reversedHistory = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("40.00"))
                .allocationType(PaymentAllocationType.AUTO_ADVANCE)
                .allocationStatus(PaymentAllocationStatus.REVERSED)
                .build();
        when(salesReceivableApi.findElectronicallyIntegratedInvoicesByOrderId(orderId))
                .thenReturn(List.of(invoiceOne));
        when(allocationRepository.findByTransactionIdOrderByAllocatedAtDesc(receiptId))
                .thenReturn(List.of(reversedHistory));

        service.autoAllocateVerifiedReceiptsForOrder(orderId, actorId);

        verify(allocationRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void reconcileOrderModificationFullCancellationReversesSourceAndCreatesRefundSettlement() {
        UUID refundTxId = UUID.randomUUID();
        PaymentInvoiceAllocation sourceAllocation = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("60.00"))
                .allocationType(PaymentAllocationType.AUTO_ADVANCE)
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .build();
        Transaction refundTx = Transaction.builder()
                .transactionId(refundTxId)
                .transactionCode("REF-001")
                .transactionType("EXPENSE")
                .category("CUSTOMER_REFUND")
                .status("VERIFIED")
                .orderId(orderId)
                .customerId(customerId)
                .amount(new BigDecimal("100.00"))
                .paymentMethod("BANK_TRANSFER")
                .createdBy(actorId)
                .transactionDate(OffsetDateTime.now())
                .build();
        when(salesReceivableApi.findInvoicesByIds(List.of(invoiceOneId))).thenReturn(List.of(invoiceOne));
        when(allocationRepository.findByOrderIdAndAllocationStatus(orderId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of(sourceAllocation));
        when(transactionRepository.findByIdForUpdate(refundTxId)).thenReturn(Optional.of(refundTx));

        service.reconcileOrderModification(new OrderModificationSettlementCommand(
                orderId, invoiceOneId, null, null, refundTxId, actorId, "Hủy toàn bộ đơn hàng"));

        assertThat(sourceAllocation.getAllocationStatus()).isEqualTo(PaymentAllocationStatus.REVERSED);
        assertThat(sourceAllocation.getReversedBy()).isEqualTo(actorId);
        assertThat(sourceAllocation.getReversalReason()).isEqualTo("Hủy toàn bộ đơn hàng");

        ArgumentCaptor<List<PaymentReceiptRefundSettlement>> settlementCaptor = ArgumentCaptor.forClass(List.class);
        verify(refundSettlementRepository).saveAllAndFlush(settlementCaptor.capture());
        assertThat(settlementCaptor.getValue()).singleElement().satisfies(settlement -> {
            assertThat(settlement.getReceiptTransactionId()).isEqualTo(receiptId);
            assertThat(settlement.getRefundTransactionId()).isEqualTo(refundTxId);
            assertThat(settlement.getSourceOrderId()).isEqualTo(orderId);
            assertThat(settlement.getSettledAmount()).isEqualByComparingTo("100.00");
            assertThat(settlement.getSettledBy()).isEqualTo(actorId);
            assertThat(settlement.getReason()).isEqualTo("Hủy toàn bộ đơn hàng");
        });
    }

    @Test
    void reconcileOrderModificationReplacementOrderCreatesRepointedAllocationAndSettlesExcessRefund() {
        UUID targetOrderId = UUID.randomUUID();
        UUID targetInvoiceId = UUID.randomUUID();
        UUID refundTxId = UUID.randomUUID();
        SalesOrderPaymentInfoDto targetOrder = SalesOrderPaymentInfoDto.builder()
                .orderId(targetOrderId)
                .customerId(customerId)
                .totalAmount(new BigDecimal("70.00"))
                .validForPayment(true)
                .build();
        SalesInvoiceDebtDto targetInvoice = new SalesInvoiceDebtDto(
                targetInvoiceId, targetOrderId, customerId, "INV-REPL", null, "MISA-REPL",
                OffsetDateTime.now(), OffsetDateTime.now(), 30, null, "ISSUED",
                new BigDecimal("70.00"), BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("70.00"),
                invoiceOneId, "STORE");
        PaymentInvoiceAllocation sourceAllocation = PaymentInvoiceAllocation.builder()
                .allocationId(UUID.randomUUID())
                .transactionId(receiptId)
                .orderId(orderId)
                .invoiceId(invoiceOneId)
                .allocatedAmount(new BigDecimal("60.00"))
                .allocationType(PaymentAllocationType.AUTO_ADVANCE)
                .allocationStatus(PaymentAllocationStatus.ACTIVE)
                .build();
        Transaction refundTx = Transaction.builder()
                .transactionId(refundTxId)
                .transactionCode("REF-002")
                .transactionType("EXPENSE")
                .category("CUSTOMER_REFUND")
                .status("VERIFIED")
                .orderId(orderId)
                .customerId(customerId)
                .amount(new BigDecimal("30.00"))
                .paymentMethod("BANK_TRANSFER")
                .createdBy(actorId)
                .transactionDate(OffsetDateTime.now())
                .build();

        when(salesOrderApi.lockOrderPaymentInfo(targetOrderId)).thenReturn(Optional.of(targetOrder));
        when(allocationRepository.findByOrderIdAndAllocationStatus(orderId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of(sourceAllocation));
        when(salesReceivableApi.findInvoicesByIds(List.of(invoiceOneId, targetInvoiceId)))
                .thenReturn(List.of(invoiceOne, targetInvoice));
        when(transactionRepository.findByIdForUpdate(refundTxId)).thenReturn(Optional.of(refundTx));

        service.reconcileOrderModification(new OrderModificationSettlementCommand(
                orderId, invoiceOneId, targetOrderId, targetInvoiceId, refundTxId, actorId,
                "Đổi sang đơn thay thế có giá trị thấp hơn"));

        assertThat(sourceAllocation.getAllocationStatus()).isEqualTo(PaymentAllocationStatus.REVERSED);

        ArgumentCaptor<List<PaymentInvoiceAllocation>> allocationCaptor = ArgumentCaptor.forClass(List.class);
        verify(allocationRepository, times(2)).saveAllAndFlush(allocationCaptor.capture());
        List<PaymentInvoiceAllocation> repointedRows = allocationCaptor.getAllValues().get(1);
        assertThat(repointedRows).singleElement().satisfies(repointed -> {
            assertThat(repointed.getTransactionId()).isEqualTo(receiptId);
            assertThat(repointed.getOrderId()).isEqualTo(targetOrderId);
            assertThat(repointed.getInvoiceId()).isEqualTo(targetInvoiceId);
            assertThat(repointed.getAllocatedAmount()).isEqualByComparingTo("70.00");
            assertThat(repointed.getAllocationType()).isEqualTo(PaymentAllocationType.REPOINTED);
            assertThat(repointed.getAllocationStatus()).isEqualTo(PaymentAllocationStatus.ACTIVE);
        });

        ArgumentCaptor<List<PaymentReceiptRefundSettlement>> settlementCaptor = ArgumentCaptor.forClass(List.class);
        verify(refundSettlementRepository).saveAllAndFlush(settlementCaptor.capture());
        assertThat(settlementCaptor.getValue()).singleElement().satisfies(settlement -> {
            assertThat(settlement.getReceiptTransactionId()).isEqualTo(receiptId);
            assertThat(settlement.getRefundTransactionId()).isEqualTo(refundTxId);
            assertThat(settlement.getSettledAmount()).isEqualByComparingTo("30.00");
        });
    }

    @Test
    void reconcileOrderModificationRejectsReplacementOrderForDifferentCustomer() {
        UUID targetOrderId = UUID.randomUUID();
        UUID targetInvoiceId = UUID.randomUUID();
        SalesOrderPaymentInfoDto differentCustomerOrder = SalesOrderPaymentInfoDto.builder()
                .orderId(targetOrderId)
                .customerId(UUID.randomUUID())
                .totalAmount(new BigDecimal("100.00"))
                .validForPayment(true)
                .build();
        when(salesOrderApi.lockOrderPaymentInfo(targetOrderId)).thenReturn(Optional.of(differentCustomerOrder));

        assertThatThrownBy(() -> service.reconcileOrderModification(new OrderModificationSettlementCommand(
                orderId, invoiceOneId, targetOrderId, targetInvoiceId, null, actorId, "Đổi sang đơn khác khách hàng")))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.INVALID_REPLACEMENT_SETTLEMENT));
        verify(allocationRepository, never()).saveAllAndFlush(any());
        verify(refundSettlementRepository, never()).saveAllAndFlush(any());
    }

    @Test
    void reconcileOrderModificationRejectsWhenRequiredRefundIsNotVerified() {
        when(salesReceivableApi.findInvoicesByIds(List.of(invoiceOneId))).thenReturn(List.of(invoiceOne));
        when(allocationRepository.findByOrderIdAndAllocationStatus(orderId, PaymentAllocationStatus.ACTIVE))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service.reconcileOrderModification(new OrderModificationSettlementCommand(
                orderId, invoiceOneId, null, null, null, actorId, "Hủy đơn nhưng chưa có phiếu hoàn tiền xác minh")))
                .isInstanceOfSatisfying(AppException.class, error ->
                        assertThat(error.getErrorCode()).isEqualTo(PaymentAllocationErrorCode.REFUND_NOT_VERIFIED));
        verify(refundSettlementRepository, never()).saveAllAndFlush(any());
    }

    private SalesInvoiceDebtDto invoice(UUID id, String code, String total) {
        return new SalesInvoiceDebtDto(id, orderId, customerId, code, null, "MISA-" + code, OffsetDateTime.now(), OffsetDateTime.now(),
                30, null, "ISSUED", new BigDecimal(total), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal(total), null, "STORE");
    }

    private CreatePaymentAllocationRequest request(PaymentAllocationLineRequest... lines) {
        return CreatePaymentAllocationRequest.builder().allocationRequestId(UUID.randomUUID())
                .allocations(List.of(lines)).reason("Reconcile issued invoices").build();
    }

    private PaymentAllocationLineRequest line(UUID invoiceId, String amount) {
        return PaymentAllocationLineRequest.builder().invoiceId(invoiceId).allocatedAmount(new BigDecimal(amount)).build();
    }
}
