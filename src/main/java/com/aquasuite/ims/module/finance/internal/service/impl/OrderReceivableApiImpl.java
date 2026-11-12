package com.aquasuite.ims.module.finance.internal.service.impl;

import com.aquasuite.ims.module.finance.api.OrderReceivableApi;
import com.aquasuite.ims.module.finance.api.dto.OrderReceivableView;
import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import com.aquasuite.ims.module.finance.internal.repository.TransactionRepository;
import com.aquasuite.ims.module.sales.api.SalesOrderApi;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderMilestoneDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;
import com.aquasuite.ims.share.exception.AppException;
import com.aquasuite.ims.share.exception.CommonErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Triển khai hợp đồng {@link OrderReceivableApi}: tổng hợp tình hình thu tiền của đơn từ sổ giao dịch
 * và thông tin đơn/mốc của Sales. Chỉ đọc, không tạo hay thay đổi giao dịch.
 */
@Service
@RequiredArgsConstructor
public class OrderReceivableApiImpl implements OrderReceivableApi {

    private static final String TYPE_RECEIPT = "RECEIPT";
    private static final String STATUS_VERIFIED = "VERIFIED";
    private static final String STATUS_PENDING = "PENDING";

    /** Truy vấn các giao dịch thu tiền của đơn. */
    private final TransactionRepository transactions;
    /** Đọc thông tin đơn và các mốc thanh toán qua hợp đồng công khai của Sales. */
    private final SalesOrderApi sales;

    /**
     * Tính đã xác minh, đang chờ, còn phải trả và tình trạng cọc của một đơn trong ảnh chụp nhất quán.
     *
     * @param orderId mã đơn cần xem
     * @return thông tin thu tiền nếu đơn tồn tại; rỗng nếu không tìm thấy đơn
     * @throws AppException nếu khoản thu mang sai mã khách so với đơn (dữ liệu không nhất quán)
     */
    @Override
    @Transactional(readOnly = true, isolation = org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Optional<OrderReceivableView> getOrderReceivable(UUID orderId) {
        if (orderId == null) {
            return Optional.empty();
        }
        Optional<SalesOrderPaymentInfoDto> orderOpt = sales.getOrderPaymentInfo(orderId);
        if (orderOpt.isEmpty()) {
            return Optional.empty();
        }
        SalesOrderPaymentInfoDto order = orderOpt.get();

        List<Transaction> receipts = transactions.findByOrderIdAndTransactionTypeIgnoreCase(orderId, TYPE_RECEIPT);
        if (receipts.stream().anyMatch(t -> !Objects.equals(order.customerId(), t.getCustomerId()))) {
            throw new AppException(CommonErrorCode.DATA_INTEGRITY_VIOLATION);
        }
        BigDecimal verified = sum(receipts, STATUS_VERIFIED);
        BigDecimal pending = sum(receipts, STATUS_PENDING);
        BigDecimal total = order.totalAmount() != null ? order.totalAmount() : BigDecimal.ZERO;
        BigDecimal remaining = total.subtract(verified).max(BigDecimal.ZERO);

        BigDecimal depositRequired = resolveDepositRequired(order);
        boolean depositSatisfied = depositRequired.signum() > 0 && verified.compareTo(depositRequired) >= 0;

        return Optional.of(new OrderReceivableView(orderId, total, verified, pending, remaining,
                order.paymentStatus(), depositRequired, depositSatisfied));
    }

    /**
     * Xác định mức cọc cần đạt: ưu tiên deposit_amount của đơn, nếu không có thì lấy số tiền của mốc đầu tiên.
     *
     * @param order thông tin đơn
     * @return mức cọc cần đạt, 0 nếu đơn không có cọc và không có mốc
     */
    private BigDecimal resolveDepositRequired(SalesOrderPaymentInfoDto order) {
        if (order.depositAmount() != null && order.depositAmount().signum() > 0) {
            return order.depositAmount();
        }
        List<SalesOrderMilestoneDto> milestones = sales.getOrderPaymentMilestones(order.orderId());
        if (!milestones.isEmpty() && milestones.get(0).amount() != null) {
            return milestones.get(0).amount();
        }
        return BigDecimal.ZERO;
    }

    /**
     * Cộng tiền các giao dịch theo trạng thái.
     * @param receipts các khoản thu của đơn
     * @param status trạng thái cần cộng
     * @return tổng tiền tương ứng
     */
    private static BigDecimal sum(List<Transaction> receipts, String status) {
        return receipts.stream().filter(t -> status.equalsIgnoreCase(t.getStatus()))
                .map(Transaction::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}
