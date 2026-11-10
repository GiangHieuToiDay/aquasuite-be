package com.aquasuite.ims.module.finance.internal.repository;

import com.aquasuite.ims.module.finance.internal.entity.Transaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
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

    /**
     * Khóa bi quan (PESSIMISTIC_WRITE) một giao dịch theo mã để quyết định UC-70
     * không bị hai kế toán xử lý đồng thời.
     * @param id mã giao dịch cần khóa
     * @return giao dịch đã khóa trong transaction hiện tại
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM Transaction t WHERE t.transactionId = :id")
    Optional<Transaction> findByIdForUpdate(@Param("id") UUID id);

    /**
     * Lấy mã đơn của một giao dịch dưới dạng scalar, KHÔNG nạp entity vào persistence context.
     * Dùng trước khi khóa để lần nạp entity đầu tiên (findByIdForUpdate) đọc được trạng thái mới nhất,
     * tránh đọc trạng thái cũ bị cache.
     * @param id mã giao dịch
     * @return mã đơn nếu giao dịch tồn tại
     */
    @Query("SELECT t.orderId FROM Transaction t WHERE t.transactionId = :id")
    Optional<UUID> findOrderIdById(@Param("id") UUID id);

    /**
     * Danh sách khoản chuyển khoản đang chờ xác minh (UC-70), lọc theo loại/phương thức/trạng thái.
     * @param transactionType loại giao dịch (RECEIPT)
     * @param paymentMethod phương thức (BANK_TRANSFER)
     * @param status trạng thái (PENDING)
     * @param pageable thông tin phân trang
     * @return trang các khoản phù hợp
     */
    Page<Transaction> findByTransactionTypeIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIgnoreCase(
            String transactionType, String paymentMethod, String status, Pageable pageable);

    /**
     * Như trên nhưng giới hạn trong một đơn cụ thể.
     * @param orderId mã đơn cần lọc
     * @param transactionType loại giao dịch (RECEIPT)
     * @param paymentMethod phương thức (BANK_TRANSFER)
     * @param status trạng thái (PENDING)
     * @param pageable thông tin phân trang
     * @return trang các khoản phù hợp thuộc đơn
     */
    Page<Transaction> findByOrderIdAndTransactionTypeIgnoreCaseAndPaymentMethodIgnoreCaseAndStatusIgnoreCase(
            UUID orderId, String transactionType, String paymentMethod, String status, Pageable pageable);



    @Query("select coalesce(sum(transaction.amount), 0) from Transaction transaction "
            + "where transaction.orderId = :orderId and upper(transaction.status) = 'VERIFIED'")
    BigDecimal sumVerifiedAmountByOrderId(@Param("orderId") UUID orderId);
}


