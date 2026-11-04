package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.audit.api.AuditQueryApi;
import com.aquasuite.ims.module.audit.api.dto.AuditLogResponse;
import com.aquasuite.ims.module.crm.api.CrmApi;
import com.aquasuite.ims.module.crm.api.dto.CustomerDebtProfileDto;
import com.aquasuite.ims.module.finance.api.event.CustomerDebtAuditEvent;
import com.aquasuite.ims.module.finance.internal.error.CustomerDebtErrorCode;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.payload.DebtStatus;
import com.aquasuite.ims.module.finance.internal.payload.request.CreditCheckRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtCallResultRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.DebtReminderRequest;
import com.aquasuite.ims.module.finance.internal.payload.request.ReasonRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.*;
import com.aquasuite.ims.module.finance.internal.notification.DebtEmailNotificationPort;
import com.aquasuite.ims.module.finance.internal.pdf.StatementDocument;
import com.aquasuite.ims.module.finance.internal.pdf.StatementPdfService;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;
import com.aquasuite.ims.module.finance.internal.service.CustomerDebtService;
import com.aquasuite.ims.module.sales.api.SalesReceivableApi;
import com.aquasuite.ims.module.sales.api.dto.SalesInvoiceDebtDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderDebtDto;
import com.aquasuite.ims.share.exception.AppException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CustomerDebtServiceImpl implements CustomerDebtService {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final String VERIFIED = "VERIFIED";
    private static final String RECEIPT = "RECEIPT";

    private final CrmApi crmApi;
    private final SalesReceivableApi salesReceivableApi;
    private final TransactionRepository transactionRepository;
    private final AuditQueryApi auditQueryApi;
    private final ApplicationEventPublisher eventPublisher;
    private final DebtEmailNotificationPort emailNotificationPort;
    private final StatementPdfService statementPdfService;
    private final FinanceLockRepository financeLocks;

    /** Kiểm tra khách bị khóa trước mọi đơn mới, kể cả đơn thanh toán ngay. */
    @Override
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void requireCustomerOrderAllowed(UUID customerId) {
        lock("customer-credit:" + customerId);
        if ("BLOCKED".equalsIgnoreCase(getCustomer(customerId).status())) {
            throw new AppException(CustomerDebtErrorCode.CUSTOMER_BLOCKED);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDebtPageResponse list(String keyword, String customerType, String debtStatus, int page, int size) {
        List<CustomerDebtSummaryResponse> summaries = calculateAll().stream()
                .filter(summary -> matches(summary, keyword, customerType, debtStatus))
                .sorted(Comparator.comparing(CustomerDebtSummaryResponse::customerCode, Comparator.nullsLast(String::compareToIgnoreCase)))
                .toList();
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        int from = Math.min(safePage * safeSize, summaries.size());
        int to = Math.min(from + safeSize, summaries.size());
        return new CustomerDebtPageResponse(summaries.subList(from, to), safePage, safeSize,
                summaries.size(), (int) Math.ceil((double) summaries.size() / safeSize));
    }

    @Override
    @Transactional(readOnly = true)
    public DebtDashboardStatsResponse stats() {
        List<CustomerDebtSummaryResponse> summaries = calculateAll();
        return DebtDashboardStatsResponse.builder()
                .totalOutstanding(summaries.stream().map(CustomerDebtSummaryResponse::outstandingBalance).reduce(ZERO, BigDecimal::add))
                .overdueCustomerCount(summaries.stream().filter(value -> value.overdueBalance().compareTo(ZERO) > 0).count())
                .creditBlockedCustomerCount(summaries.stream().filter(value -> value.debtStatus() == DebtStatus.CREDIT_BLOCKED
                        || value.debtStatus() == DebtStatus.BLACKLISTED || value.debtStatus() == DebtStatus.BLOCKED).count())
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public CustomerDebtDetailResponse detail(UUID customerId) {
        CustomerDebtProfileDto customer = getCustomer(customerId);
        DebtCalculation calculation = calculate(customer, documentsFor(Set.of(customerId)));
        List<AuditLogResponse> history = auditQueryApi.findByEntity("CUSTOMER", customerId).stream()
                .filter(entry -> entry.getAction().startsWith("DEBT_") || entry.getAction().startsWith("CREDIT_")
                        || entry.getAction().startsWith("STATEMENT_") || entry.getAction().startsWith("CUSTOMER_"))
                .toList();
        return new CustomerDebtDetailResponse(customer, calculation.summary(), calculation.invoices(),
                calculation.orders(), calculation.receipts(), calculation.unallocatedReceipts(),
                calculation.unallocatedReceiptAmount(), history);
    }

    @Override
    @Transactional(readOnly = true)
    public BlacklistStatusResponse blacklistStatus(UUID customerId) {
        CustomerDebtDetailResponse detail = detail(customerId);
        CustomerDebtSummaryResponse summary = detail.summary();
        boolean blacklisted = summary.debtStatus() == DebtStatus.BLACKLISTED;
        String reason = summary.debtStatus() == DebtStatus.BLOCKED
                ? "CUSTOMER_STATUS_BLOCKED"
                : blacklisted ? "OUTSTANDING_ISSUED_INVOICE_OVER_60_DAYS" : "NOT_BLACKLISTED";
        return new BlacklistStatusResponse(customerId, detail.customer().status(),
                summary.debtStatus(), blacklisted, summary.overdueBalance(), reason);
    }

    /** Kiểm tra công nợ, kỳ hạn 1–30 ngày và hạn mức đối với giá trị đơn dự kiến. */
    @Override
    @Transactional(readOnly = true)
    public CreditCheckResponse checkCredit(UUID customerId, CreditCheckRequest request) {
        lock("customer-credit:" + customerId);
        CustomerDebtProfileDto customer = getCustomer(customerId);
        DebtCalculation calculation = calculate(customer, documentsFor(Set.of(customerId)));
        boolean counter = "COUNTER".equalsIgnoreCase(request.orderChannel()) || "POS".equalsIgnoreCase(request.orderChannel());
        boolean eligibleType = "CONTRACTOR".equalsIgnoreCase(customer.customerType())
                || "PROJECT_CUSTOMER".equalsIgnoreCase(customer.customerType());
        boolean validCreditTerm = customer.creditTermDays() != null
                && customer.creditTermDays() >= 1 && customer.creditTermDays() <= 30;
        boolean approved = counter && eligibleType && validCreditTerm && calculation.summary().debtStatus() == DebtStatus.WITHIN_TERM
                && calculation.summary().availableCredit().compareTo(request.newOrderAmount()) >= 0;
        String reason = approved ? "APPROVED"
                : calculation.summary().debtStatus() == DebtStatus.BLOCKED ? "CUSTOMER_BLOCKED"
                : calculation.summary().debtStatus() == DebtStatus.BLACKLISTED ? "BLACKLISTED"
                : !validCreditTerm ? "INVALID_CREDIT_TERM"
                : reasonFor(customer, calculation, counter, eligibleType, request.newOrderAmount());
        return CreditCheckResponse.builder().customerId(customerId).approved(approved)
                .overrideAllowed(!approved && validCreditTerm && calculation.summary().debtStatus() != DebtStatus.BLACKLISTED
                        && calculation.summary().debtStatus() != DebtStatus.BLOCKED)
                .debtStatus(calculation.summary().debtStatus()).reasonCode(reason)
                .creditLimit(calculation.summary().creditLimit()).outstandingBalance(calculation.summary().outstandingBalance())
                .overdueBalance(calculation.summary().overdueBalance()).availableCredit(calculation.summary().availableCredit()).build();
    }

    @Override
    @Transactional
    public void approveOverride(UUID orderId, ReasonRequest request, UUID actorId) {
        lock("order-credit-override:" + orderId);
        SalesOrderDebtDto order = salesReceivableApi.findOrderById(orderId)
                .orElseThrow(() -> new AppException(CustomerDebtErrorCode.INVALID_CREDIT_REQUEST));
        CustomerDebtProfileDto customer = getCustomer(order.customerId());
        DebtStatus status = calculate(customer, documentsFor(Set.of(customer.customerId()))).summary().debtStatus();
        if (status == DebtStatus.BLACKLISTED || status == DebtStatus.BLOCKED
                || customer.creditTermDays() == null || customer.creditTermDays() < 1 || customer.creditTermDays() > 30) {
            throw new AppException(CustomerDebtErrorCode.OVERRIDE_NOT_ALLOWED);
        }
        boolean alreadyOverridden = auditQueryApi.findByEntity("ORDER", orderId).stream()
                .anyMatch(entry -> "CREDIT_OVERRIDE_APPROVED".equals(entry.getAction()));
        if (alreadyOverridden) {
            throw new AppException(CustomerDebtErrorCode.OVERRIDE_NOT_ALLOWED);
        }
        publish(actorId, orderId, "ORDER", "CREDIT_OVERRIDE_APPROVED", Map.of(),
                Map.of("customerId", customer.customerId(), "orderCode", order.orderCode(), "orderAmount", order.totalAmount()), request.reason());
    }

    @Override
    @Transactional
    public DebtReminderResponse sendReminder(UUID customerId, DebtReminderRequest request, UUID actorId) {
        CustomerDebtDetailResponse detail = detail(customerId);
        if (detail.summary().outstandingBalance().compareTo(ZERO) == 0) {
            return new DebtReminderResponse(ZERO, "SKIPPED", "NOT_CONFIGURED", "Customer has paid in full.");
        }
        String emailResult = "SKIPPED";
        if (detail.customer().email() != null && !detail.customer().email().isBlank()) {
            try {
                emailNotificationPort.send(detail.customer().email(), "[AquaSuite] Debt reminder",
                        reminderBody(detail, request.reminderType()), null, null);
                emailResult = "SENT";
            } catch (RuntimeException exception) {
                emailResult = "FAILED";
            }
        }
        Map<String, Object> data = new HashMap<>();
        data.put("reminderType", request.reminderType().name());
        data.put("outstandingBalance", detail.summary().outstandingBalance());
        data.put("emailResult", emailResult);
        data.put("smsResult", "NOT_CONFIGURED");
        publish(actorId, customerId, "SENT".equals(emailResult) ? "DEBT_REMINDER_SENT" : "DEBT_REMINDER_FAILED", Map.of(), data, null);
        return new DebtReminderResponse(detail.summary().outstandingBalance(), emailResult, "NOT_CONFIGURED",
                "SENT".equals(emailResult) ? "Debt reminder dispatched successfully." : "Email reminder was not dispatched.");
    }

    @Override
    @Transactional
    public void saveCallResult(UUID customerId, DebtCallResultRequest request, UUID actorId) {
        getCustomer(customerId);
        Map<String, Object> data = new HashMap<>();
        data.put("channel", "PHONE_CALL");
        data.put("callResult", request.callResult().name());
        if (request.promisedPaymentDate() != null) data.put("promisedPaymentDate", request.promisedPaymentDate().toString());
        if (request.note() != null) data.put("note", request.note());
        publish(actorId, customerId, "DEBT_CALL_RECORDED", Map.of(), data, request.note());
    }

    @Override
    @Transactional(readOnly = true)
    public StatementDocument exportStatement(UUID customerId, UUID actorId) {
        StatementDocument document = statementPdfService.generate(detail(customerId));
        publish(actorId, customerId, "STATEMENT_EXPORTED", Map.of(), Map.of("fileName", document.fileName()), null);
        return document;
    }

    @Override
    @Transactional
    public StatementEmailResponse emailStatement(UUID customerId, UUID actorId) {
        CustomerDebtDetailResponse detail = detail(customerId);
        if (detail.customer().email() == null || detail.customer().email().isBlank()) {
            throw new AppException(CustomerDebtErrorCode.INVALID_CREDIT_REQUEST, "Customer email is required to send a statement.");
        }
        StatementDocument document = statementPdfService.generate(detail);
        try {
            emailNotificationPort.send(detail.customer().email(), "[AquaSuite] Statement of account",
                    "Attached is your internal AquaSuite statement of account.", document.bytes(), document.fileName());
            publish(actorId, customerId, "STATEMENT_EMAILED", Map.of(), Map.of("fileName", document.fileName()), null);
            return new StatementEmailResponse("SENT", "Statement emailed successfully.");
        } catch (RuntimeException exception) {
            publish(actorId, customerId, "STATEMENT_EMAIL_FAILED", Map.of(),
                    Map.of("fileName", document.fileName(), "failureType", exception.getClass().getSimpleName()), null);
            return new StatementEmailResponse("FAILED", "Statement email was not dispatched; retry manually after correcting email configuration.");
        }
    }

    @Override
    @Transactional
    public void block(UUID customerId, ReasonRequest request, UUID actorId) {
        lock("customer-credit:" + customerId);
        CustomerDebtProfileDto customer = getCustomer(customerId);
        crmApi.updateCustomerStatus(customerId, "BLOCKED");
        publish(actorId, customerId, "CUSTOMER_BLOCKED", Map.of("status", customer.status()),
                Map.of("status", "BLOCKED"), request.reason());
    }

    @Override
    @Transactional
    public void restore(UUID customerId, ReasonRequest request, UUID actorId) {
        lock("customer-credit:" + customerId);
        CustomerDebtProfileDto customer = getCustomer(customerId);
        crmApi.updateCustomerStatus(customerId, "ACTIVE");
        publish(actorId, customerId, "CUSTOMER_RESTORED", Map.of("status", customer.status()),
                Map.of("status", "ACTIVE"), request.reason());
    }

    private List<CustomerDebtSummaryResponse> calculateAll() {
        List<CustomerDebtProfileDto> customers = crmApi.findAllDebtProfiles();
        Set<UUID> ids = customers.stream().map(CustomerDebtProfileDto::customerId).collect(Collectors.toSet());
        Documents documents = documentsFor(ids);
        return customers.stream().map(customer -> calculate(customer, documents).summary()).toList();
    }

    private Documents documentsFor(Set<UUID> customerIds) {
        List<SalesOrderDebtDto> orders = salesReceivableApi.findOrdersByCustomerIds(customerIds);
        List<SalesInvoiceDebtDto> invoices = salesReceivableApi.findInvoicesByCustomerIds(customerIds);
        List<Transaction> transactions = transactionRepository.findByCustomerIdInAndStatusIgnoreCase(customerIds, VERIFIED).stream()
                .filter(transaction -> RECEIPT.equalsIgnoreCase(transaction.getTransactionType()))
                .toList();
        return new Documents(orders, invoices, transactions);
    }

    /**
     * Tính dư đơn, dư hóa đơn đã phát hành và khoản thu chưa gắn với hóa đơn tính nợ.
     * Hóa đơn STORE không được dùng để tính hạn nợ; khoản thu của đơn vẫn được tính một lần.
     * @param customer hồ sơ và điều kiện tín dụng của khách
     * @param documents đơn, hóa đơn và khoản thu đã xác minh được đọc cho khách
     * @return kết quả tổng hợp công nợ và các danh sách chi tiết
     */
    private DebtCalculation calculate(CustomerDebtProfileDto customer, Documents documents) {
        List<SalesOrderDebtDto> orders = documents.orders().stream()
                .filter(order -> customer.customerId().equals(order.customerId()))
                .filter(order -> !"CANCELLED".equalsIgnoreCase(order.orderStatus())).toList();
        List<SalesInvoiceDebtDto> invoices = documents.invoices().stream()
                .filter(invoice -> customer.customerId().equals(invoice.customerId()))
                // Hóa đơn cửa hàng không làm phát sinh hạn nợ hóa đơn điện tử, kể cả khi ISSUED.
                .filter(invoice -> "ISSUED".equalsIgnoreCase(invoice.status()))
                .filter(invoice -> !"STORE".equalsIgnoreCase(invoice.invoiceType())).toList();
        Map<UUID, UUID> invoiceOrder = invoices.stream().collect(Collectors.toMap(SalesInvoiceDebtDto::invoiceId, SalesInvoiceDebtDto::orderId));
        Map<UUID, BigDecimal> orderPaid = new HashMap<>();
        Map<UUID, BigDecimal> invoicePaid = new HashMap<>();
        BigDecimal unallocated = ZERO;
        List<UnallocatedReceiptResponse> unallocatedReceipts = new ArrayList<>();
        List<PaymentReceiptResponse> receiptResponses = new ArrayList<>();
        for (Transaction transaction : documents.transactions()) {
            if (!customer.customerId().equals(transaction.getCustomerId())) continue;
            UUID orderId = transaction.getOrderId() != null ? transaction.getOrderId() : invoiceOrder.get(transaction.getInvoiceId());
            if (orderId != null) orderPaid.merge(orderId, amount(transaction.getAmount()), BigDecimal::add);
            // Chỉ hóa đơn đã phát hành mới nhận tiền trực tiếp; hóa đơn cửa hàng còn chờ phân bổ.
            if (transaction.getInvoiceId() != null && invoiceOrder.containsKey(transaction.getInvoiceId())) {
                invoicePaid.merge(transaction.getInvoiceId(), amount(transaction.getAmount()), BigDecimal::add);
            } else if (transaction.getOrderId() != null) {
                BigDecimal receiptAmount = amount(transaction.getAmount());
                unallocated = unallocated.add(receiptAmount);
                unallocatedReceipts.add(new UnallocatedReceiptResponse(transaction.getTransactionId(),
                        transaction.getTransactionCode(), transaction.getOrderId(), receiptAmount, transaction.getTransactionDate()));
            }
            receiptResponses.add(PaymentReceiptResponse.builder()
                    .transactionId(transaction.getTransactionId()).transactionCode(transaction.getTransactionCode())
                    .orderId(transaction.getOrderId()).invoiceId(transaction.getInvoiceId()).amount(amount(transaction.getAmount()))
                    .paymentMethod(transaction.getPaymentMethod()).transactionDate(transaction.getTransactionDate())
                    .status(transaction.getStatus()).build());
        }
        BigDecimal outstanding = orders.stream().map(order -> positive(amount(order.totalAmount()).subtract(orderPaid.getOrDefault(order.orderId(), ZERO))))
                .reduce(ZERO, BigDecimal::add);
        List<OrderDebtResponse> orderResponses = orders.stream().map(order -> OrderDebtResponse.builder()
                .orderId(order.orderId()).orderCode(order.orderCode()).orderChannel(order.orderChannel())
                .orderStatus(order.orderStatus()).paymentStatus(order.paymentStatus()).dueDate(order.dueDate())
                .totalAmount(amount(order.totalAmount())).verifiedPaidAmount(orderPaid.getOrDefault(order.orderId(), ZERO))
                .outstandingAmount(positive(amount(order.totalAmount()).subtract(orderPaid.getOrDefault(order.orderId(), ZERO))))
                .build()).toList();
        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        BigDecimal overdue = ZERO;
        boolean blacklisted = false;
        List<InvoiceDebtResponse> invoiceResponses = new ArrayList<>();
        LocalDate nearestDue = null;
        for (SalesInvoiceDebtDto invoice : invoices) {
            BigDecimal paid = invoicePaid.getOrDefault(invoice.invoiceId(), ZERO);
            BigDecimal invoiceOutstanding = positive(amount(invoice.totalAmount()).subtract(paid));
            LocalDate due = invoice.issueDate().toLocalDate().plusDays(customer.creditTermDays() == null ? 0 : customer.creditTermDays());
            if (invoiceOutstanding.compareTo(ZERO) > 0) {
                if (nearestDue == null || due.isBefore(nearestDue)) nearestDue = due;
                if (due.isBefore(today)) overdue = overdue.add(invoiceOutstanding);
                if (invoice.issueDate().toLocalDate().plusDays(60).isBefore(today)) blacklisted = true;
            }
            invoiceResponses.add(InvoiceDebtResponse.builder().invoiceId(invoice.invoiceId()).invoiceCode(invoice.invoiceCode())
                    .invoiceSymbol(invoice.invoiceSymbol()).orderId(invoice.orderId()).issueDate(invoice.issueDate())
                    .signedAt(invoice.signedAt()).dueDate(due).status(invoice.status()).totalAmount(amount(invoice.totalAmount()))
                    .verifiedPaidAmount(paid).outstandingAmount(invoiceOutstanding).build());
        }
        BigDecimal limit = amount(customer.creditLimit());
        BigDecimal available = limit.subtract(outstanding);
        DebtStatus status = resolveStatus(customer, limit, outstanding, overdue, blacklisted);
        CustomerDebtSummaryResponse summary = CustomerDebtSummaryResponse.builder().customerId(customer.customerId())
                .customerCode(customer.customerCode()).customerName(customer.customerName()).phone(customer.phone())
                .companyName(customer.companyName()).customerType(customer.customerType()).creditLimit(limit)
                .outstandingBalance(outstanding).overdueBalance(overdue).availableCredit(available)
                .nearestDueDate(nearestDue).debtStatus(status).build();
        return new DebtCalculation(summary, invoiceResponses, orderResponses, receiptResponses, unallocated, unallocatedReceipts);
    }

    private DebtStatus resolveStatus(CustomerDebtProfileDto customer, BigDecimal limit, BigDecimal outstanding,
                                     BigDecimal overdue, boolean blacklisted) {
        if ("BLOCKED".equalsIgnoreCase(customer.status())) return DebtStatus.BLOCKED;
        if (blacklisted) return DebtStatus.BLACKLISTED;
        if (overdue.compareTo(ZERO) > 0) return DebtStatus.OVERDUE;
        if (limit.compareTo(ZERO) <= 0) return DebtStatus.NO_CREDIT;
        if (outstanding.compareTo(limit) > 0) return DebtStatus.CREDIT_BLOCKED;
        return DebtStatus.WITHIN_TERM;
    }

    private boolean matches(CustomerDebtSummaryResponse value, String keyword, String customerType, String debtStatus) {
        boolean keywordMatches = keyword == null || keyword.isBlank() || contains(value.customerCode(), keyword)
                || contains(value.customerName(), keyword) || contains(value.companyName(), keyword) || contains(value.phone(), keyword);
        return keywordMatches && (customerType == null || customerType.isBlank() || customerType.equalsIgnoreCase(value.customerType()))
                && (debtStatus == null || debtStatus.isBlank() || debtStatus.equalsIgnoreCase(value.debtStatus().name()));
    }

    private String reasonFor(CustomerDebtProfileDto customer, DebtCalculation calculation, boolean counter, boolean eligibleType, BigDecimal proposed) {
        if ("BLOCKED".equalsIgnoreCase(customer.status())) return "CUSTOMER_BLOCKED";
        if (calculation.summary().debtStatus() == DebtStatus.BLACKLISTED) return "BLACKLISTED";
        if (!counter || !eligibleType || calculation.summary().creditLimit().compareTo(ZERO) <= 0) return "CREDIT_NOT_AVAILABLE";
        if (calculation.summary().overdueBalance().compareTo(ZERO) > 0) return "OVERDUE";
        if (calculation.summary().availableCredit().compareTo(proposed) < 0) return "LIMIT_EXCEEDED";
        return "CREDIT_NOT_AVAILABLE";
    }

    private CustomerDebtProfileDto getCustomer(UUID customerId) {
        return crmApi.getDebtProfile(customerId).orElseThrow(() -> new AppException(CustomerDebtErrorCode.CUSTOMER_NOT_FOUND));
    }

    private void publish(UUID actorId, UUID customerId, String action, Map<String, Object> oldData, Map<String, Object> newData, String reason) {
        publish(actorId, customerId, "CUSTOMER", action, oldData, newData, reason);
    }

    private void publish(UUID actorId, UUID entityId, String entityType, String action, Map<String, Object> oldData, Map<String, Object> newData, String reason) {
        eventPublisher.publishEvent(new CustomerDebtAuditEvent(UUID.randomUUID(), actorId, entityId, entityType, action,
                oldData, newData, reason, Instant.now()));
    }

    private String reminderBody(CustomerDebtDetailResponse detail, DebtReminderRequest.ReminderType type) {
        return "Dear " + detail.customer().customerName() + ",\n"
                + (type == DebtReminderRequest.ReminderType.UPCOMING ? "This is a friendly upcoming payment reminder. " : "Your payment is overdue. ")
                + "Outstanding amount: " + detail.summary().outstandingBalance() + ".";
    }

    /** Transaction-scoped lock serializes finance actions for one business aggregate. */
    private void lock(String key) {
        financeLocks.lockCreditKey(key);
    }

    private static BigDecimal amount(BigDecimal value) { return value == null ? ZERO : value; }
    private static BigDecimal positive(BigDecimal value) { return value.max(ZERO); }
    private static boolean contains(String value, String keyword) { return value != null && value.toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)); }

    private record Documents(List<SalesOrderDebtDto> orders, List<SalesInvoiceDebtDto> invoices, List<Transaction> transactions) {}
    private record DebtCalculation(CustomerDebtSummaryResponse summary, List<InvoiceDebtResponse> invoices,
                                   List<OrderDebtResponse> orders, List<PaymentReceiptResponse> receipts,
                                   BigDecimal unallocatedReceiptAmount,
                                   List<UnallocatedReceiptResponse> unallocatedReceipts) {}
}
