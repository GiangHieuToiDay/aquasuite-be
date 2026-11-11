package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.error.OrderPaymentErrorCode;
import com.aquasuite.ims.module.finance.internal.payload.request.RecordOrderPaymentRequest;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderMilestoneProgress;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderMilestoneProgressResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.OrderPaymentSummary;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.repository.FinanceLockRepository;
import com.aquasuite.ims.module.finance.internal.service.OrderPaymentService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderMilestoneDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Xử lý ghi nhận và tính toán các khoản thanh toán đã lưu cho một đơn hàng. */
@Service
@RequiredArgsConstructor
public class OrderPaymentServiceImpl implements OrderPaymentService {
    /** Truy vấn và lưu các giao dịch thu tiền. */
    private final TransactionRepository transactions;
    /** Truy vấn, khóa đơn và cập nhật trạng thái thanh toán qua hợp đồng Sales. */
    private final SalesOrderApi sales;
    /** Xác định nhân viên đang thực hiện yêu cầu. */
    private final IamApi iam;
    /** Giữ khóa PostgreSQL theo mã chống ghi trùng trong transaction hiện tại. */
    private final FinanceLockRepository locks;
    private final com.aquasuite.ims.module.finance.internal.repository.CodRepository cod;

    /**
     * Khóa đơn, kiểm tra yêu cầu gửi lại và số còn phải trả, rồi lưu một giao dịch.
     * Tiền mặt thực nhận là VERIFIED; chuyển khoản khai báo là PENDING.
     * Với tiền mặt, trạng thái thanh toán của đơn được cập nhật trong cùng transaction.
     * @param orderId mã đơn nhận thanh toán
     * @param request thông tin khoản thu và mã chống ghi trùng
     * @return giao dịch vừa ghi hoặc giao dịch cũ khi gửi lại cùng yêu cầu
     * @throws AppException nếu đơn không hợp lệ, số tiền vượt dư hoặc mã yêu cầu bị dùng khác nội dung
     */
    @Override
    @Transactional
    public OrderPaymentResponse record(UUID orderId, RecordOrderPaymentRequest request) {
        UUID actorId = Objects.requireNonNull(iam.getCurrentUserId(), "Authenticated actor required");
        // Khóa theo key trước khóa đơn để hai đơn dùng cùng key không cùng ghi giao dịch.
        locks.lockPaymentKey("order-payment-key:" + request.idempotencyKey());
        SalesOrderPaymentInfoDto order = sales.lockOrderPaymentInfo(orderId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        var previous = transactions.findByIdempotencyKey(request.idempotencyKey());
        if (previous.isPresent()) {
            Transaction old = previous.get();
            if (!orderId.equals(old.getOrderId()) || !actorId.equals(old.getCreatedBy())
                    || old.getAmount().compareTo(request.amount()) != 0
                    || !request.paymentMethod().name().equals(old.getPaymentMethod())
                    || !Objects.equals(old.getReferenceNo(), request.referenceNo())
                    || !Objects.equals(old.getDescription(), request.description())) {
                throw new AppException(OrderPaymentErrorCode.DUPLICATE_KEY);
            }
            return response(old);
        }
        if (!order.validForPayment() || order.totalAmount() == null || order.totalAmount().signum() <= 0) {
            throw new AppException(OrderPaymentErrorCode.ORDER_NOT_PAYABLE);
        }
        var existing = payments(order);
        var summary = summary(order, existing);
        if (request.amount().signum() <= 0 || request.amount().compareTo(summary.remaining().subtract(cod.held(orderId))) > 0) {
            throw new AppException(OrderPaymentErrorCode.AMOUNT_EXCEEDS_BALANCE);
        }
        UUID id = UUID.randomUUID();
        OffsetDateTime now = OffsetDateTime.now();
        boolean cash = request.paymentMethod() == RecordOrderPaymentRequest.Method.CASH;
        Transaction transaction = Transaction.builder().transactionId(id).idempotencyKey(request.idempotencyKey())
                .transactionCode("PAY-" + id).transactionType("RECEIPT").category("CUSTOMER_PAYMENT")
                .customerId(order.customerId()).orderId(orderId).invoiceId(order.storeInvoiceId()).amount(request.amount())
                .paymentMethod(request.paymentMethod().name()).referenceNo(request.referenceNo())
                .description(request.description()).status(cash ? "VERIFIED" : "PENDING")
                .transactionDate(now).createdBy(actorId).approvedBy(cash ? actorId : null)
                .approvedAt(cash ? now : null).createdAt(now).build();
        transactions.saveAndFlush(transaction);
        if (cash) {
            BigDecimal verified = summary.verifiedPaid().add(request.amount());
            sales.updatePaymentStatus(orderId, verified.compareTo(order.totalAmount()) == 0 ? "PAID" : "PARTIALLY_PAID");
        }
        return response(transaction);
    }

    /**
     * Tính tổng đã xác minh, đang chờ và còn phải trả từ một ảnh chụp dữ liệu nhất quán.
     * @param orderId mã đơn cần tính
     * @return tổng quan thanh toán của đơn
     * @throws AppException nếu không tìm thấy đơn hoặc dữ liệu khoản thu không nhất quán
     */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderPaymentSummary summary(UUID orderId) {
        var order = sales.getOrderPaymentInfo(orderId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        return summary(order, payments(order));
    }

    /**
     * Trả lịch sử giao dịch thu tiền của đơn sau khi kiểm tra đơn tồn tại.
     * @param orderId mã đơn cần xem lịch sử
     * @return các giao dịch thu tiền thuộc đơn
     * @throws AppException nếu không tìm thấy đơn hoặc dữ liệu khoản thu không nhất quán
     */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public List<OrderPaymentResponse> history(UUID orderId) {
        var order = sales.getOrderPaymentInfo(orderId).orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        return payments(order).stream().map(OrderPaymentServiceImpl::response).toList();
    }

    /**
     * Tính tiến độ thanh toán theo từng mốc của đơn trong một ảnh chụp dữ liệu nhất quán.
     *
     * <p>Số liệu toàn đơn dùng lại cách tính của {@link #summary}: chỉ cộng khoản
     * {@code VERIFIED} vào đã trả, {@code PENDING} hiển thị riêng. Tổng đã xác minh được
     * phân bổ lần lượt vào các mốc theo thứ tự thỏa thuận do Sales cung cấp; mỗi mốc nhận
     * tối đa phần còn thiếu của mốc, phần dư chảy sang mốc tiếp theo cùng đơn. Việc đọc đơn,
     * giao dịch và mốc đều nằm trong cùng transaction readonly để bảo đảm nhất quán; không
     * tạo thêm giao dịch và không đếm trùng số tiền.</p>
     *
     * @param orderId mã đơn cần tính tiến độ
     * @return tổng quan toàn đơn kèm tiến độ từng mốc
     * @throws AppException nếu không tìm thấy đơn hoặc dữ liệu khoản thu không nhất quán
     */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public OrderMilestoneProgressResponse milestoneProgress(UUID orderId) {
        var order = sales.getOrderPaymentInfo(orderId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        OrderPaymentSummary summary = summary(order, payments(order));
        List<SalesOrderMilestoneDto> milestones = sales.getOrderPaymentMilestones(orderId);

        List<OrderMilestoneProgress> progress = new ArrayList<>();
        // Số tiền đã xác minh còn lại để phân bổ tuần tự vào các mốc.
        BigDecimal remainingVerified = summary.verifiedPaid();
        for (SalesOrderMilestoneDto milestone : milestones) {
            BigDecimal agreed = milestone.amount() != null ? milestone.amount() : BigDecimal.ZERO;
            // Mốc chỉ nhận tối đa phần còn thiếu của mốc; phần dư giữ lại cho mốc sau.
            BigDecimal paid = remainingVerified.min(agreed).max(BigDecimal.ZERO);
            remainingVerified = remainingVerified.subtract(paid);
            progress.add(new OrderMilestoneProgress(
                    milestone.milestoneId(), milestone.stageName(), milestone.sortOrder(),
                    agreed, paid, agreed.subtract(paid)));
        }
        return new OrderMilestoneProgressResponse(order.orderId(), summary.totalAmount(),
                summary.verifiedPaid(), summary.pendingAmount(), summary.remaining(),
                !progress.isEmpty(), List.copyOf(progress));
    }

    /**
     * Lấy các khoản thu của đơn và phát hiện giao dịch mang sai mã khách hàng.
     * @param order thông tin đơn dùng để kiểm tra mã khách
     * @return danh sách giao dịch loại RECEIPT
     * @throws AppException nếu khoản thu thuộc khách khác với khách của đơn
     */
    private List<Transaction> payments(SalesOrderPaymentInfoDto order) {
        List<Transaction> receipts = transactions.findByOrderIdAndTransactionTypeIgnoreCase(order.orderId(), "RECEIPT");
        if (receipts.stream().anyMatch(t -> !Objects.equals(order.customerId(), t.getCustomerId()))) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        return receipts;
    }
    /**
     * Tính tổng thanh toán; chỉ cộng VERIFIED vào số đã trả, PENDING được thể hiện riêng.
     * @param order thông tin và tổng tiền đơn
     * @param payments các khoản thu của đơn
     * @return tổng quan và trạng thái thanh toán tính từ giao dịch
     */
    private static OrderPaymentSummary summary(SalesOrderPaymentInfoDto order, List<Transaction> payments) {
        BigDecimal verified = sum(payments, "VERIFIED");
        BigDecimal pending = sum(payments, "PENDING");
        BigDecimal remaining = order.totalAmount().subtract(verified).max(BigDecimal.ZERO);
        String status = verified.signum() == 0 ? "UNPAID"
                : remaining.signum() == 0 ? "PAID" : "PARTIALLY_PAID";
        return new OrderPaymentSummary(order.orderId(), order.totalAmount(), verified, pending, remaining, status);
    }
    /**
     * Cộng số tiền của các giao dịch theo trạng thái được yêu cầu.
     * @param payments các khoản thu cần tính
     * @param status trạng thái cần cộng
     * @return tổng tiền của các khoản có trạng thái tương ứng
     */
    private static BigDecimal sum(List<Transaction> payments, String status) {
        return payments.stream().filter(t -> status.equalsIgnoreCase(t.getStatus()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    /**
     * Chuyển giao dịch nội bộ thành thông tin trả cho client.
     * @param t giao dịch cần chuyển đổi
     * @return dữ liệu giao dịch cho API
     */
    private static OrderPaymentResponse response(Transaction t) {
        return new OrderPaymentResponse(t.getTransactionId(), t.getTransactionCode(), t.getOrderId(), t.getInvoiceId(), t.getAmount(),
                t.getPaymentMethod(), t.getStatus(), t.getReferenceNo(), t.getDescription(), t.getRejectReason(),
                t.getTransactionDate(), t.getCreatedBy());
    }
}
