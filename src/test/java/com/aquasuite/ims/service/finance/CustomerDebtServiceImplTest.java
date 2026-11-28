package com.aquasuite.ims.service.finance;

import com.aquasuite.ims.service.audit.AuditLogService;
import com.aquasuite.ims.service.crm.CustomerService;
import com.aquasuite.ims.dto.crm.response.CustomerDebtProfileDto;
import com.aquasuite.ims.event.finance.CustomerDebtAuditEvent;
import com.aquasuite.ims.entity.finance.PaymentInvoiceAllocation;
import com.aquasuite.ims.entity.finance.Transaction;
import com.aquasuite.ims.enums.finance.PaymentAllocationStatus;
import com.aquasuite.ims.exception.finance.CustomerDebtErrorCode;
import com.aquasuite.ims.integration.finance.DebtEmailNotificationPort;
import com.aquasuite.ims.enums.finance.DebtStatus;
import com.aquasuite.ims.dto.finance.request.CreditCheckRequest;
import com.aquasuite.ims.dto.finance.request.DebtCallResultRequest;
import com.aquasuite.ims.dto.finance.request.DebtReminderRequest;
import com.aquasuite.ims.dto.finance.request.ReasonRequest;
import com.aquasuite.ims.dto.finance.response.StatementDocument;
import com.aquasuite.ims.service.finance.StatementPdfService;
import com.aquasuite.ims.repository.finance.FinanceLockRepository;
import com.aquasuite.ims.repository.finance.PaymentInvoiceAllocationRepository;
import com.aquasuite.ims.repository.finance.PaymentReceiptRefundSettlementRepository;
import com.aquasuite.ims.repository.finance.TransactionRepository;
import com.aquasuite.ims.service.finance.impl.CustomerDebtServiceImpl;
import com.aquasuite.ims.service.sales.SalesQueryService;
import com.aquasuite.ims.dto.sales.response.SalesInvoiceDebtDto;
import com.aquasuite.ims.dto.sales.response.SalesOrderDebtDto;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDebtServiceImplTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID INVOICE_ID = UUID.fromString("10000000-0000-4000-8000-000000000003");
    private static final UUID ACTOR_ID = UUID.fromString("10000000-0000-4000-8000-000000000009");

    @Mock private CustomerService crmApi;
    @Mock private SalesQueryService salesReceivableApi;
    @Mock private TransactionRepository transactionRepository;
    @Mock private PaymentInvoiceAllocationRepository paymentInvoiceAllocationRepository;
    @Mock private PaymentReceiptRefundSettlementRepository paymentReceiptRefundSettlementRepository;
    @Mock private AuditLogService auditQueryApi;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private DebtEmailNotificationPort emailNotificationPort;
    @Mock private StatementPdfService statementPdfService;
    @Mock private FinanceLockRepository financeLocks;

    @InjectMocks private CustomerDebtServiceImpl service;

    private CustomerDebtProfileDto customer;
    private SalesOrderDebtDto order;
    private SalesInvoiceDebtDto invoice;

    @BeforeEach
    void setUp() {
        customer = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang", "0903118240",
                "ketoan@example.com", "CONTRACTOR", "0312456789", "An Khang Co.", List.of(), List.of(),
                new BigDecimal("500.00"), 30, "ACTIVE");
        order = new SalesOrderDebtDto(ORDER_ID, CUSTOMER_ID, "ORD-100", "COUNTER", "CONFIRMED", "UNPAID",
                new BigDecimal("200.00"), BigDecimal.ZERO, new BigDecimal("200.00"), LocalDate.now().plusDays(30));
        invoice = new SalesInvoiceDebtDto(INVOICE_ID, ORDER_ID, CUSTOMER_ID, "INV-100", "MISA-INV-100",
                "MISA-INV-100", OffsetDateTime.now(), OffsetDateTime.now(), 30, LocalDate.now().plusDays(30), "ISSUED", new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("200.00"), null, "STORE");
        lenient().when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(order));
        lenient().when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(invoice));
    }

    @Test
    void creditAuthorizationRequiresValidCreditTerm() {
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of());
        CreditCheckRequest request = new CreditCheckRequest(null, "COUNTER", new BigDecimal("100.00"));
        for (Integer term : new Integer[] {null, 0, 31, 1, 30}) {
            CustomerDebtProfileDto profile = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang",
                    "0903118240", "ketoan@example.com", "CONTRACTOR", "0312456789", "An Khang Co.",
                    List.of(), List.of(), new BigDecimal("500.00"), term, "ACTIVE");
            when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(profile));

            var result = service.checkCredit(CUSTOMER_ID, request);
            boolean expected = term != null && term >= 1 && term <= 30;
            assertEquals(expected, result.approved(), "credit term=" + term);
            if (!expected) {
                assertEquals("INVALID_CREDIT_TERM", result.reasonCode());
                assertEquals(false, result.overrideAllowed());
            }
        }
    }

    @Test
    void listKeepsStoreLinkedReceiptUnallocatedUntilUc63AndDoesNotDoubleCountExposure() {
        Transaction directInvoiceReceipt = receipt(INVOICE_ID, ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.findAllDebtProfiles()).thenReturn(List.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(directInvoiceReceipt));

        var page = service.list(null, null, null, "customerCode", "asc", 0, 20);

        assertEquals(1, page.content().size());
        assertMoney("150.00", page.content().getFirst().outstandingBalance());
        assertMoney("0.00", page.content().getFirst().unbilledCommitment());
        assertMoney("200.00", page.content().getFirst().issuedInvoiceReceivable());
        assertMoney("350.00", page.content().getFirst().availableCredit());
    }

    @Test
    void detailLeavesOrderOnlyReceiptUnallocatedFromItsInvoice() {
        Transaction orderOnlyReceipt = receipt(null, ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(orderOnlyReceipt));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var detail = service.detail(CUSTOMER_ID);

        assertMoney("150.00", detail.summary().outstandingBalance());
        assertMoney("200.00", detail.invoices().getFirst().outstandingAmount());
        assertMoney("50.00", detail.unallocatedVerifiedReceiptAmount());
        assertEquals(1, detail.unallocatedOrderReceipts().size());
        assertEquals(1, detail.orders().size());
        assertMoney("150.00", detail.orders().getFirst().outstandingAmount());
        assertEquals(1, detail.verifiedReceipts().size());
        assertTrue(detail.reminderHistory().isEmpty());
    }

    @Test
    void detailUsesUc63AllocationForIssuedInvoiceWithoutDoubleCountingReceipt() {
        Transaction orderOnlyReceipt = receipt(null, ORDER_ID, new BigDecimal("50.00"));
        PaymentInvoiceAllocation allocation = PaymentInvoiceAllocation.builder().transactionId(orderOnlyReceipt.getTransactionId())
                .orderId(ORDER_ID).invoiceId(INVOICE_ID).allocatedAmount(new BigDecimal("50.00"))
                .allocationStatus(PaymentAllocationStatus.ACTIVE).build();
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(orderOnlyReceipt));
        when(paymentInvoiceAllocationRepository.findByTransactionIdInAndAllocationStatus(any(), eq(PaymentAllocationStatus.ACTIVE)))
                .thenReturn(List.of(allocation));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var detail = service.detail(CUSTOMER_ID);

        assertMoney("150.00", detail.summary().outstandingBalance());
        assertMoney("150.00", detail.invoices().getFirst().outstandingAmount());
        assertMoney("0.00", detail.unallocatedVerifiedReceiptAmount());
        assertTrue(detail.unallocatedOrderReceipts().isEmpty());
    }

    @Test
    void receiptLinkedToStoreInvoiceIsUnallocatedToIssuedDeliveryInvoice() {
        Transaction receipt = receipt(UUID.randomUUID(), ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(receipt));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());
        var detail = service.detail(CUSTOMER_ID);
        assertMoney("150.00", detail.orders().getFirst().outstandingAmount());
        assertMoney("200.00", detail.invoices().getFirst().outstandingAmount());
        assertMoney("50.00", detail.unallocatedVerifiedReceiptAmount());
    }

    @Test
    void storeInvoiceReceiptRemainsUnallocatedWhenDeliveryInvoiceIsIssued() {
        UUID storeInvoiceId = UUID.randomUUID();
        SalesOrderDebtDto order100 = new SalesOrderDebtDto(ORDER_ID, CUSTOMER_ID, "ORD-100", "COUNTER",
                "CONFIRMED", "PARTIALLY_PAID", new BigDecimal("100.00"), BigDecimal.ZERO,
                new BigDecimal("100.00"), LocalDate.now().plusDays(30));
        SalesInvoiceDebtDto storeInvoice = new SalesInvoiceDebtDto(storeInvoiceId, ORDER_ID, CUSTOMER_ID,
                "STORE-100", null, null, OffsetDateTime.now(), null, null, null, "DRAFT", new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), null, "STORE");
        SalesInvoiceDebtDto deliveryInvoice = new SalesInvoiceDebtDto(INVOICE_ID, ORDER_ID, CUSTOMER_ID,
                "INV-40", "MISA-INV-40", "MISA-INV-40", OffsetDateTime.now(), OffsetDateTime.now(), 30, LocalDate.now().plusDays(30), "ISSUED", new BigDecimal("40.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.00"), null, "STORE");
        when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(order100));
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(storeInvoice, deliveryInvoice));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(receipt(storeInvoiceId, ORDER_ID, new BigDecimal("30.00"))));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var detail = service.detail(CUSTOMER_ID);

        assertMoney("70.00", detail.summary().outstandingBalance());
        assertMoney("30.00", detail.unallocatedVerifiedReceiptAmount());
        assertEquals(1, detail.unallocatedOrderReceipts().size());
        assertEquals(1, detail.invoices().size());
        assertMoney("40.00", detail.invoices().getFirst().outstandingAmount());
    }

    @Test
    void issuedStoreInvoiceWithoutElectronicIntegrationNeverStartsInvoiceDebtOrAllocatesReceipt() {
        SalesInvoiceDebtDto store = new SalesInvoiceDebtDto(INVOICE_ID, ORDER_ID, CUSTOMER_ID,
                "STORE-1", null, null, OffsetDateTime.now().minusDays(100), null, null, null, "ISSUED",
                new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("200.00"), null, "STORE");
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(store));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(receipt(INVOICE_ID, ORDER_ID, new BigDecimal("50.00"))));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var detail = service.detail(CUSTOMER_ID);

        assertMoney("150.00", detail.summary().outstandingBalance());
        assertMoney("0.00", detail.summary().overdueBalance());
        assertMoney("50.00", detail.unallocatedVerifiedReceiptAmount());
        assertTrue(detail.invoices().isEmpty());
    }

    @Test
    void creditCheckAcceptsLegacyCounterAliases() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of());
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of());
        for (String channel : List.of("COUNTER", "POS")) {
            assertTrue(service.checkCredit(CUSTOMER_ID,
                    new CreditCheckRequest(null, channel, new BigDecimal("100.00"))).approved(), channel);
        }
        assertEquals(false, service.checkCredit(CUSTOMER_ID,
                new CreditCheckRequest(null, "IN_STORE", new BigDecimal("100.00"))).approved());
    }

    @Test
    void noCreditProfileKeepsReceivableButNeverReportsNegativeAvailableCredit() {
        SalesOrderDebtDto paidInSalesOnly = new SalesOrderDebtDto(ORDER_ID, CUSTOMER_ID, "ORD-PAID", "COUNTER",
                "COMPLETED", "PAID", new BigDecimal("200.00"), BigDecimal.ZERO,
                new BigDecimal("200.00"), null);
        CustomerDebtProfileDto noCreditProfile = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang",
                "0903118240", "ketoan@example.com", "RETAIL", null, null,
                List.of(), List.of(), BigDecimal.ZERO, null, "ACTIVE");
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(noCreditProfile));
        when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(paidInSalesOnly));
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of());
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());

        var summary = service.creditSummary(CUSTOMER_ID);

        // Finance receipts, rather than Sales.paymentStatus, are the money source of truth.
        assertMoney("200.00", summary.outstandingBalance());
        assertMoney("0.00", summary.availableCredit());
        assertEquals(DebtStatus.NO_CREDIT, summary.debtStatus());
    }

    @Test
    void draftOrderWaitingForCreditApprovalDoesNotSelfConsumeAvailableCredit() {
        SalesOrderDebtDto draftOrder = new SalesOrderDebtDto(ORDER_ID, CUSTOMER_ID, "ORD-DRAFT", "DIRECT_SALES",
                "DRAFT", "UNPAID", new BigDecimal("500.00"), BigDecimal.ZERO,
                new BigDecimal("500.00"), LocalDate.now().plusDays(30));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(draftOrder));
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of());
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());

        var check = service.checkCredit(CUSTOMER_ID,
                new CreditCheckRequest(ORDER_ID, "DIRECT_SALES", new BigDecimal("500.00")));

        assertTrue(check.approved());
        assertMoney("0.00", check.outstandingBalance());
        assertMoney("500.00", check.availableCredit());
    }

    @Test
    void blockLocksCustomerUpdatesStatusToBlockedAndPublishesAuditEvent() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));

        service.block(CUSTOMER_ID, new ReasonRequest("Khách chây ì nợ quá hạn trên 60 ngày"), ACTOR_ID);

        verify(financeLocks).lockCreditKey("customer-credit:" + CUSTOMER_ID);
        verify(crmApi).updateCustomerStatus(CUSTOMER_ID, "BLOCKED");
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "CUSTOMER_BLOCKED".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && ACTOR_ID.equals(event.actorId())
                        && "ACTIVE".equals(event.oldData().get("status"))
                        && "BLOCKED".equals(event.newData().get("status"))
                        && "Khách chây ì nợ quá hạn trên 60 ngày".equals(event.reason())));
    }

    @Test
    void requireCustomerOrderAllowedRejectsBlockedCustomer() {
        CustomerDebtProfileDto blockedCustomer = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang",
                "0903118240", "ketoan@example.com", "CONTRACTOR", "0312456789", "An Khang Co.",
                List.of(), List.of(), new BigDecimal("500.00"), 30, "BLOCKED");
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(blockedCustomer));

        AppException ex = assertThrows(AppException.class, () -> service.requireCustomerOrderAllowed(CUSTOMER_ID));
        assertEquals(CustomerDebtErrorCode.CUSTOMER_BLOCKED, ex.getErrorCode());
    }

    @Test
    void restoreLocksCustomerUpdatesStatusToActiveAndPublishesAuditEvent() {
        CustomerDebtProfileDto blockedCustomer = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang",
                "0903118240", "ketoan@example.com", "CONTRACTOR", "0312456789", "An Khang Co.",
                List.of(), List.of(), new BigDecimal("500.00"), 30, "BLOCKED");
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(blockedCustomer));

        service.restore(CUSTOMER_ID, new ReasonRequest("Khách đã thanh toán đủ toàn bộ công nợ"), ACTOR_ID);

        verify(financeLocks).lockCreditKey("customer-credit:" + CUSTOMER_ID);
        verify(crmApi).updateCustomerStatus(CUSTOMER_ID, "ACTIVE");
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "CUSTOMER_RESTORED".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && ACTOR_ID.equals(event.actorId())
                        && "BLOCKED".equals(event.oldData().get("status"))
                        && "ACTIVE".equals(event.newData().get("status"))
                        && "Khách đã thanh toán đủ toàn bộ công nợ".equals(event.reason())));
    }

    @Test
    void sendReminderDispatchesEmailWhenOutstandingPositiveAndPublishesSentAudit() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var response = service.sendReminder(
                CUSTOMER_ID,
                new DebtReminderRequest(DebtReminderRequest.ReminderType.OVERDUE),
                ACTOR_ID);

        assertEquals("SENT", response.emailResult());
        assertEquals("NOT_CONFIGURED", response.smsResult());
        assertMoney("200.00", response.outstandingBalance());
        verify(emailNotificationPort).send(
                eq("ketoan@example.com"),
                eq("[AquaSuite] Debt reminder"),
                any(),
                isNull(),
                isNull());
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "DEBT_REMINDER_SENT".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && "OVERDUE".equals(event.newData().get("reminderType"))));
    }

    @Test
    void sendReminderSkipsEmailAndAuditWhenCustomerHasPaidInFull() {
        Transaction fullOrderReceipt = receipt(null, ORDER_ID, new BigDecimal("200.00"));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(fullOrderReceipt));
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var response = service.sendReminder(
                CUSTOMER_ID,
                new DebtReminderRequest(DebtReminderRequest.ReminderType.UPCOMING),
                ACTOR_ID);

        assertEquals("SKIPPED", response.emailResult());
        assertEquals("NOT_CONFIGURED", response.smsResult());
        assertMoney("0.00", response.outstandingBalance());
        verify(emailNotificationPort, never()).send(any(), any(), any(), any(), any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void saveCallResultPublishesAuditEventWithPromisedDateAndNote() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        LocalDate promisedDate = LocalDate.of(2026, 12, 5);
        DebtCallResultRequest request = new DebtCallResultRequest(
                DebtCallResultRequest.CallResult.PROMISED_TO_PAY,
                promisedDate,
                "Khách hẹn chuyển khoản thứ Sáu");

        service.saveCallResult(CUSTOMER_ID, request, ACTOR_ID);

        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "DEBT_CALL_RECORDED".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && ACTOR_ID.equals(event.actorId())
                        && "PHONE_CALL".equals(event.newData().get("channel"))
                        && "PROMISED_TO_PAY".equals(event.newData().get("callResult"))
                        && "2026-12-05".equals(event.newData().get("promisedPaymentDate"))
                        && "Khách hẹn chuyển khoản thứ Sáu".equals(event.reason())));
    }

    @Test
    void exportStatementGeneratesPdfAndPublishesExportAuditEvent() {
        byte[] pdfBytes = new byte[] {'%', 'P', 'D', 'F', '-', '1'};
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());
        when(statementPdfService.generate(any()))
                .thenReturn(new StatementDocument("sao-ke-cong-no-KH-001.pdf", pdfBytes));

        StatementDocument exported = service.exportStatement(CUSTOMER_ID, ACTOR_ID);

        assertEquals("sao-ke-cong-no-KH-001.pdf", exported.fileName());
        assertArrayEquals(pdfBytes, exported.bytes());
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "STATEMENT_EXPORTED".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && "sao-ke-cong-no-KH-001.pdf".equals(event.newData().get("fileName"))));
    }

    @Test
    void emailStatementSendsPdfAttachmentAndPublishesSentAuditEvent() {
        byte[] pdfBytes = new byte[] {'%', 'P', 'D', 'F'};
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());
        when(statementPdfService.generate(any()))
                .thenReturn(new StatementDocument("sao-ke-cong-no-KH-001.pdf", pdfBytes));

        var result = service.emailStatement(CUSTOMER_ID, ACTOR_ID);

        assertEquals("SENT", result.emailResult());
        verify(emailNotificationPort).send(
                eq("ketoan@example.com"),
                eq("[AquaSuite] Statement of account"),
                any(),
                eq(pdfBytes),
                eq("sao-ke-cong-no-KH-001.pdf"));
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null
                        && "STATEMENT_EMAILED".equals(event.action())
                        && CUSTOMER_ID.equals(event.entityId())
                        && "sao-ke-cong-no-KH-001.pdf".equals(event.newData().get("fileName"))));
    }

    @Test
    void emailStatementWithoutCustomerEmailThrowsInvalidCreditRequest() {
        CustomerDebtProfileDto customerWithoutEmail = new CustomerDebtProfileDto(CUSTOMER_ID, "KH-001", "An Khang",
                "0903118240", " ", "CONTRACTOR", "0312456789", "An Khang Co.",
                List.of(), List.of(), new BigDecimal("500.00"), 30, "ACTIVE");
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customerWithoutEmail));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        AppException ex = assertThrows(AppException.class, () -> service.emailStatement(CUSTOMER_ID, ACTOR_ID));
        assertEquals(CustomerDebtErrorCode.INVALID_CREDIT_REQUEST, ex.getErrorCode());
        verify(statementPdfService, never()).generate(any());
    }

    @Test
    void statementEmailFailureReturnsARecoverableResultAndPublishesAuditEvent() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());
        when(statementPdfService.generate(any())).thenReturn(new StatementDocument("statement-KH-001.pdf", new byte[] {1}));
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailNotificationPort)
                .send(any(), any(), any(), any(), any());

        var result = service.emailStatement(CUSTOMER_ID, UUID.randomUUID());

        assertEquals("FAILED", result.emailResult());
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                event != null && "STATEMENT_EMAIL_FAILED".equals(event.action()) && CUSTOMER_ID.equals(event.entityId())));
    }

    @Test
    void myDebtDetailResolvesCustomerByUserIdAndStripsInternalReminderHistory() {
        UUID userId = UUID.randomUUID();
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.of(
                com.aquasuite.ims.dto.crm.response.CustomerSummaryDto.builder()
                        .customerId(CUSTOMER_ID)
                        .userId(userId)
                        .customerCode("KH-001")
                        .customerName("An Khang")
                        .status("ACTIVE")
                        .build()));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());

        var result = service.myDebtDetail(userId);

        assertMoney("200.00", result.summary().outstandingBalance());
        assertEquals(1, result.invoices().size());
        assertEquals(1, result.orders().size());
        assertTrue(result.reminderHistory().isEmpty());
        verify(crmApi).getCustomerByUserId(userId);
    }

    @Test
    void myDebtDetailThrowsCustomerNotFoundWhenProfileMissing() {
        UUID userId = UUID.randomUUID();
        when(crmApi.getCustomerByUserId(userId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class, () -> service.myDebtDetail(userId));
        assertEquals(CustomerDebtErrorCode.CUSTOMER_NOT_FOUND, ex.getErrorCode());
    }

    private Transaction receipt(UUID invoiceId, UUID orderId, BigDecimal amount) {
        return Transaction.builder().transactionId(UUID.randomUUID()).transactionCode("TXT-100")
                .transactionType("RECEIPT").category("CUSTOMER_PAYMENT").customerId(CUSTOMER_ID)
                .invoiceId(invoiceId).orderId(orderId).amount(amount).paymentMethod("BANK_TRANSFER")
                .transactionDate(OffsetDateTime.now()).status("VERIFIED").createdBy(UUID.randomUUID()).build();
    }

    private void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }
}
