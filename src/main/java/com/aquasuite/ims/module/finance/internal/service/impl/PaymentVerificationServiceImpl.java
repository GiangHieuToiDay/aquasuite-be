package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.error.OrderPaymentErrorCode;
import com.aquasuite.ims.module.finance.internal.error.PaymentVerificationErrorCode;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationPageResponse;
import com.aquasuite.ims.module.finance.internal.payload.response.PaymentVerificationResponse;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.finance.internal.service.PaymentVerificationService;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Triển khai UC-70: xem, xác minh và từ chối khoản chuyển khoản đang chờ. */
@Service
@RequiredArgsConstructor
public class PaymentVerificationServiceImpl implements PaymentVerificationService {

    private static final String TYPE_RECEIPT = "RECEIPT";
    private static final String METHOD_TRANSFER = "BANK_TRANSFER";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_REJECTED = "REJECTED";

    /** Truy vấn, khóa và lưu giao dịch thu tiền. */
    private final TransactionRepository transactions;
    /** Khóa đơn và cập nhật trạng thái thanh toán qua hợp đồng công khai của Sales. */
    private final SalesOrderApi sales;
    /** Xác định kế toán đang thực hiện quyết định. */
    private final IamApi iam;

    /**
     * Liệt kê khoản chuyển khoản đang chờ xác minh theo thứ tự ghi nhận, có thể lọc theo đơn.
     * @param orderId mã đơn để lọc; null là tất cả
     * @param page chỉ số trang (từ 0)
     * @param size kích thước trang
     * @return trang các khoản đang chờ
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentVerificationPageResponse list(UUID orderId, String status, int page, int size) {
        String st = (status == null || status.isBlank()) ? STATUS_PENDING : status.trim().toUpperCase();
        // Khoản đang chờ: cũ nhất trước để xử lý theo thứ tự; khoản đã xử lý: mới nhất trước theo thời điểm quyết định.
        Pageable pageable = STATUS_PENDING.equals(st)
                ? PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size, Sort.by(Sort.Direction.ASC, "transactionDate"))
                : PageRequest.of(Math.max(page, 0), size <= 0 ? 20 : size, Sort.by(Sort.Direction.DESC, "approvedAt"));
        Page<Transaction> result = orderId == null
                ? transactions.findByTransactionTypeIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIgnoreCase(
                        TYPE_RECEIPT, METHOD_TRANSFER, st, pageable)
                : transactions.findByOrderIdAndTransactionTypeIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIgnoreCase(
                        orderId, TYPE_RECEIPT, METHOD_TRANSFER, st, pageable);
        return new PaymentVerificationPageResponse(
                result.getContent().stream().map(PaymentVerificationServiceImpl::response).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    /**
     * Xem chi tiết một khoản chuyển khoản để đối chiếu; chỉ chấp nhận khoản RECEIPT/BANK_TRANSFER.
     * @param paymentId mã giao dịch
     * @return thông tin khoản
     * @throws AppException nếu không tìm thấy hoặc không phải khoản chuyển khoản của đơn
     */
    @Override
    @Transactional(readOnly = true)
    public PaymentVerificationResponse detail(UUID paymentId) {
        Transaction t = transactions.findById(paymentId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
        if (!isBankTransferReceipt(t)) {
            throw new AppException(PaymentVerificationErrorCode.NOT_PENDING_TRANSFER);
        }
        return response(t);
    }

    /**
     * Xác minh một khoản: khóa đơn trước (nhất quán UC-23), khóa lại giao dịch, tính lại nghĩa vụ
     * từ các khoản VERIFIED và chỉ xác minh khi không vượt số còn phải trả; cập nhật trạng thái Sales.
     * @param paymentId mã giao dịch cần xác minh
     * @return khoản sau khi xác minh
     * @throws AppException nếu khoản không hợp lệ, đơn không nhận thanh toán, hoặc vượt nghĩa vụ
     */
    @Override
    @Transactional
    public PaymentVerificationResponse verify(UUID paymentId) {
        // Lấy orderId bằng truy vấn scalar (không nạp entity) để lần nạp entity đầu tiên nằm dưới khóa,
        // bảo đảm đọc trạng thái mới nhất và tránh race double-apply khi hai kế toán xử lý đồng thời.
        UUID orderId = transactions.findOrderIdById(paymentId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
        if (orderId == null) {
            throw new AppException(PaymentVerificationErrorCode.NOT_PENDING_TRANSFER);
        }
        UUID actorId = Objects.requireNonNull(iam.getCurrentUserId(), "Authenticated actor required");

        // Khóa đơn trước (giống UC-23) để tuần tự hóa với ghi tiền và xác minh khác cùng đơn.
        SalesOrderPaymentInfoDto order = sales.lockOrderPaymentInfo(orderId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.ORDER_NOT_FOUND));
        if (!order.validForPayment() || order.totalAmount() == null) {
            throw new AppException(OrderPaymentErrorCode.ORDER_NOT_PAYABLE);
        }

        // Khóa lại chính giao dịch và kiểm tra trạng thái mới nhất (chống hai kế toán xử lý trùng).
        Transaction locked = transactions.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
        requirePendingBankTransfer(locked);
        if (!orderId.equals(locked.getOrderId())) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }

        // Tính lại số đã xác minh của đơn (không trừ PENDING khác) và số còn phải trả.
        BigDecimal verified = verifiedPaid(order);
        BigDecimal pendingCod = transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, TYPE_RECEIPT).stream()
                .filter(t -> t.getDeliveryId() != null && "COD".equals(t.getPaymentType()) && STATUS_PENDING.equals(t.getStatus()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remaining = order.totalAmount().subtract(verified).subtract(pendingCod).max(BigDecimal.ZERO);
        if (locked.getAmount().compareTo(remaining) > 0) {
            // Vượt nghĩa vụ: báo lỗi, giữ nguyên PENDING (transaction rollback), không tự hoàn/chuyển đơn.
            throw new AppException(PaymentVerificationErrorCode.OVER_OBLIGATION);
        }

        OffsetDateTime now = OffsetDateTime.now();
        locked.setStatus(STATUS_VERIFIED);
        locked.setApprovedBy(actorId);
        locked.setApprovedAt(now);
        transactions.saveAndFlush(locked);

        BigDecimal newVerified = verified.add(locked.getAmount());
        sales.updatePaymentStatus(orderId,
                newVerified.compareTo(order.totalAmount()) == 0 ? "PAID" : "PARTIALLY_PAID");
        return response(locked);
    }

    /**
     * Từ chối một khoản đang chờ với lý do bắt buộc; ghi người/thời điểm, không thay đổi số đã thu.
     * @param paymentId mã giao dịch cần từ chối
     * @param reason lý do từ chối
     * @return khoản sau khi từ chối
     * @throws AppException nếu thiếu lý do hoặc khoản không ở trạng thái chờ hợp lệ
     */
    @Override
    @Transactional
    public PaymentVerificationResponse reject(UUID paymentId, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new AppException(PaymentVerificationErrorCode.REASON_REQUIRED);
        }
        UUID actorId = Objects.requireNonNull(iam.getCurrentUserId(), "Authenticated actor required");
        Transaction locked = transactions.findByIdForUpdate(paymentId)
                .orElseThrow(() -> new AppException(OrderPaymentErrorCode.PAYMENT_NOT_FOUND));
        requirePendingBankTransfer(locked);

        OffsetDateTime now = OffsetDateTime.now();
        locked.setStatus(STATUS_REJECTED);
        locked.setApprovedBy(actorId);
        locked.setApprovedAt(now);
        locked.setRejectReason(reason.trim());
        transactions.saveAndFlush(locked);
        return response(locked);
    }

    /**
     * Tính tổng các khoản RECEIPT/VERIFIED của đơn; phát hiện giao dịch mang sai mã khách.
     * @param order thông tin đơn dùng để kiểm tra mã khách
     * @return tổng tiền đã xác minh của đơn
     */
    private BigDecimal verifiedPaid(SalesOrderPaymentInfoDto order) {
        List<Transaction> receipts = transactions
                .findByOrderIdAndTransactionTypeIgnoreCase(order.orderId(), TYPE_RECEIPT);
        if (receipts.stream().anyMatch(t -> !Objects.equals(order.customerId(), t.getCustomerId()))) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        return receipts.stream().filter(t -> STATUS_VERIFIED.equalsIgnoreCase(t.getStatus()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Khoản phải là RECEIPT + BANK_TRANSFER thì mới thuộc phạm vi UC-70. */
    private static boolean isBankTransferReceipt(Transaction t) {
        return TYPE_RECEIPT.equalsIgnoreCase(t.getTransactionType())
                && METHOD_TRANSFER.equalsIgnoreCase(t.getPaymentMethod());
    }

    /**
     * Bắt buộc khoản là RECEIPT/BANK_TRANSFER và đang PENDING; nếu đã xử lý thì báo 409.
     * @param t giao dịch cần kiểm tra
     */
    private static void requirePendingBankTransfer(Transaction t) {
        if (!isBankTransferReceipt(t)) {
            throw new AppException(PaymentVerificationErrorCode.NOT_PENDING_TRANSFER);
        }
        if (!STATUS_PENDING.equalsIgnoreCase(t.getStatus())) {
            throw new AppException(PaymentVerificationErrorCode.NOT_PENDING);
        }
    }

    /**
     * Chuyển giao dịch nội bộ thành DTO cho API UC-70.
     * @param t giao dịch cần chuyển đổi
     * @return DTO phản hồi
     */
    private static PaymentVerificationResponse response(Transaction t) {
        return new PaymentVerificationResponse(t.getTransactionId(), t.getTransactionCode(), t.getOrderId(),
                t.getCustomerId(), t.getAmount(), t.getPaymentMethod(), t.getStatus(), t.getReferenceNo(),
                t.getDescription(), t.getRejectReason(), t.getTransactionDate(), t.getCreatedBy(),
                t.getApprovedBy(), t.getApprovedAt());
    }
}
