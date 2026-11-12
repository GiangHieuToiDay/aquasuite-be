package com.aquasuite.ims.module.sales.api;

import com.aquasuite.ims.module.sales.api.dto.SalesOrderMilestoneDto;
import com.aquasuite.ims.module.sales.api.dto.SalesOrderPaymentInfoDto;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Public synchronous interface của module Sales (UC-21 / UC-22).
 * Phục vụ module Finance (UC-23: Manage Payment & UC-70: Verify Bank Transfer Payment)
 * truy vấn tổng tiền đơn hàng và cập nhật trạng thái thanh toán của đơn.
 */
public interface SalesOrderApi {

    /**
     * Lấy thông tin đơn hàng để kiểm tra đơn tồn tại, còn hiệu lực và lấy tổng tiền cần thanh toán.
     *
     * @param orderId ID đơn hàng
     * @return Optional chứa thông tin thanh toán của đơn hàng
     */
    Optional<SalesOrderPaymentInfoDto> getOrderPaymentInfo(UUID orderId);

    /**
     * Khóa đơn trong transaction của bên gọi để các lần ghi thanh toán không tính trùng số dư.
     * @param orderId mã đơn cần khóa
     * @return thông tin đơn được khóa nếu đơn tồn tại
     */
    Optional<SalesOrderPaymentInfoDto> lockOrderPaymentInfo(UUID orderId);

    /**
     * Cập nhật trạng thái thanh toán của đơn hàng (UNPAID, PARTIALLY_PAID, PAID)
     * sau khi UC-23 hoặc UC-70 tính lại tổng các giao dịch Verified. Bên gọi phải ở trong
     * transaction, khóa đơn qua lockOrderPaymentInfo trước khi đọc tiền và tổng đơn,
     * rồi giữ khóa đến commit/rollback. Mọi thay đổi tổng đơn cũng phải dùng khóa đó
     * và tính lại trạng thái từ giao dịch đã xác minh trong cùng transaction.
     *
     * @param orderId       ID đơn hàng
     * @param paymentStatus Trạng thái thanh toán mới (UNPAID, PARTIALLY_PAID, PAID)
     */
    void updatePaymentStatus(UUID orderId, String paymentStatus);

    /**
     * Lấy snapshot chỉ đọc các mốc thanh toán theo thỏa thuận của đơn, phục vụ UC-23
     * tính tiến độ thanh toán theo từng mốc.
     *
     * <p>Các mốc được sắp theo thứ tự ổn định (theo {@code sortOrder} tăng dần, và dùng
     * {@code milestoneId} làm tiêu chí phụ khi trùng thứ tự) để Finance tính tiền đã
     * xác minh lần lượt vào từng mốc. Đơn tạo trực tiếp có thể không có mốc nào; khi đó
     * danh sách trả về rỗng. Phương thức chỉ đọc, không tạo hay thay đổi dữ liệu mốc.</p>
     *
     * @param orderId mã đơn hàng cần lấy các mốc; nếu {@code null} trả về danh sách rỗng
     * @return danh sách mốc thanh toán đã sắp thứ tự ổn định, rỗng nếu đơn không có mốc
     */
    List<SalesOrderMilestoneDto> getOrderPaymentMilestones(UUID orderId);
}
