package com.aquasuite.ims.module.finance.internal.service;

import com.aquasuite.ims.module.audit.api.AuditQueryApi;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.finance.api.event.CustomerDebtAuditEvent;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.notification.DebtEmailNotificationPort;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.pdf.StatementDocument;
import com.aquasuite.ims.module.finance.internal.pdf.StatementPdfService;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.service.impl.CustomerDebtServiceImpl;
import com.aquasuite.ims.module.sales.api.SalesReceivableApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderDebtDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomerDebtServiceImplTest {
    private static final UUID CUSTOMER_ID = UUID.fromString("10000000-0000-4000-8000-000000000001");
    private static final UUID ORDER_ID = UUID.fromString("10000000-0000-4000-8000-000000000002");
    private static final UUID INVOICE_ID = UUID.fromString("10000000-0000-4000-8000-000000000003");

    @Mock private CrmApi crmApi;
    @Mock private SalesReceivableApi salesReceivableApi;
    @Mock private TransactionRepository transactionRepository;
    @Mock private AuditQueryApi auditQueryApi;
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
                OffsetDateTime.now(), null, 30, LocalDate.now().plusDays(30), "ISSUED", new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("200.00"), null, "ELECTRONIC");
        when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(order));
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(invoice));
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
            when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(profile));

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
    void listUsesOrdersAsTheOnlyOutstandingSourceAndDoesNotDoubleCountInvoices() {
        Transaction directInvoiceReceipt = receipt(INVOICE_ID, ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.findAllDebtProfiles()).thenReturn(List.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED")))
                .thenReturn(List.of(directInvoiceReceipt));

        var page = service.list(null, null, null, 0, 20);

        assertEquals(1, page.content().size());
        assertMoney("150.00", page.content().getFirst().outstandingBalance());
        assertMoney("0.00", page.content().getFirst().unbilledCommitment());
        assertMoney("150.00", page.content().getFirst().issuedInvoiceReceivable());
        assertMoney("350.00", page.content().getFirst().availableCredit());
    }

    @Test
    void detailLeavesOrderOnlyReceiptUnallocatedFromItsInvoice() {
        Transaction orderOnlyReceipt = receipt(null, ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
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
    void receiptLinkedToStoreInvoiceIsUnallocatedToIssuedDeliveryInvoice() {
        Transaction receipt = receipt(UUID.randomUUID(), ORDER_ID, new BigDecimal("50.00"));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
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
                "STORE-100", null, OffsetDateTime.now(), null, null, null, "DRAFT", new BigDecimal("100.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("100.00"), null, "STORE");
        SalesInvoiceDebtDto deliveryInvoice = new SalesInvoiceDebtDto(INVOICE_ID, ORDER_ID, CUSTOMER_ID,
                "INV-40", "MISA-INV-40", OffsetDateTime.now(), null, 30, LocalDate.now().plusDays(30), "ISSUED", new BigDecimal("40.00"),
                BigDecimal.ZERO, BigDecimal.ZERO, new BigDecimal("40.00"), null, "ELECTRONIC");
        when(salesReceivableApi.findOrdersByCustomerIds(any())).thenReturn(List.of(order100));
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(storeInvoice, deliveryInvoice));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
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
    void issuedStoreInvoiceNeverStartsInvoiceDebtOrAllocatesReceipt() {
        SalesInvoiceDebtDto store = new SalesInvoiceDebtDto(INVOICE_ID, ORDER_ID, CUSTOMER_ID,
                "STORE-1", null, OffsetDateTime.now().minusDays(100), null, null, null, "ISSUED",
                new BigDecimal("200.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("200.00"), null, "STORE");
        when(salesReceivableApi.findInvoicesByCustomerIds(any())).thenReturn(List.of(store));
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
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
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
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
    void statementEmailFailureReturnsARecoverableResultAndPublishesAuditEvent() {
        when(crmApi.getDebtProfile(CUSTOMER_ID)).thenReturn(java.util.Optional.of(customer));
        when(transactionRepository.findByCustomerIdInAndStatusIgnoreCase(any(), eq("VERIFIED"))).thenReturn(List.of());
        when(auditQueryApi.findByEntity(any(), any())).thenReturn(List.of());
        when(statementPdfService.generate(any())).thenReturn(new StatementDocument("statement-KH-001.pdf", new byte[] {1}));
        doThrow(new IllegalStateException("SMTP unavailable")).when(emailNotificationPort)
                .send(any(), any(), any(), any(), any());

        var result = service.emailStatement(CUSTOMER_ID, UUID.randomUUID());

        assertEquals("FAILED", result.emailResult());
        verify(eventPublisher).publishEvent(argThat((CustomerDebtAuditEvent event) ->
                "STATEMENT_EMAIL_FAILED".equals(event.action()) && CUSTOMER_ID.equals(event.entityId())));
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
