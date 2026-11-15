package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.finance.api.event.CustomerDebtAuditEvent;
import com.aquasuite.ims.module.finance.internal.entity.PaymentInvoiceAllocation;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.enums.PaymentAllocationStatus;
import com.aquasuite.ims.module.finance.internal.error.PaymentAllocationErrorCode;
import com.aquasuite.ims.module.finance.internal.mapper.PaymentAllocationMapper;
import com.aquasuite.ims.module.finance.internal.payload.request.CreatePaymentAllocationRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.PaymentAllocationLineRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentAllocationLineResponse;
import com.aquasuite.ims.module.finance.internal.repository.PaymentInvoiceAllocationRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceImplTest {
    @Mock TransactionRepository transactionRepository;
    @Mock PaymentInvoiceAllocationRepository allocationRepository;
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
