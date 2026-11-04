package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

/** Truy vấn giao dịch phục vụ theo dõi thanh toán và công nợ khách hàng. */
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {
    /**
     * Tìm giao dịch của các khách theo trạng thái, không phân biệt chữ hoa thường.
     * @param customerIds danh sách mã khách hàng
     * @param status trạng thái cần tìm
     * @return các giao dịch phù hợp
     */
    List<Transaction> findByCustomerIdInAndStatusIgnoreCase(Collection<UUID> customerIds, String status);
    /**
     * Lấy giao dịch theo đơn và loại thu/chi để tính lịch sử, tổng tiền thanh toán.
     * @param orderId mã đơn hàng
     * @param transactionType loại giao dịch cần tìm
     * @return các giao dịch phù hợp
     */
    List<Transaction> findByOrderIdAndTransactionTypeIgnoreCase(UUID orderId, String transactionType);
    /**
     * Tìm yêu cầu đã xử lý để tránh tạo thêm giao dịch khi client gửi lại.
     * @param idempotencyKey mã chống ghi trùng của yêu cầu
     * @return giao dịch đã ghi nếu mã tồn tại
     */
    Optional<Transaction> findByIdempotencyKey(UUID idempotencyKey);
}
