package com.aquasuite.ims.module.inventory.internal.service;

import com.aquasuite.ims.module.inventory.internal.payload.req.CreateGoodsReceiptRequest;
import com.aquasuite.ims.module.inventory.internal.payload.res.GoodsReceiptResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

/**
 * Nghiệp vụ Quản lý nhập kho theo đơn mua hàng (UC-32).
 *
 * <p>Triển khai theo Hướng B đã chốt: lập và xác nhận phiếu là một hành động atomic, phiếu sau
 * xác nhận là bất biến; không có trạng thái nháp. Việc sửa sai số liệu sau xác nhận (điều chỉnh
 * tồn) nằm ngoài phạm vi UC-32.</p>
 */
public interface GoodsReceiptService {

    /**
     * Lập và xác nhận một phiếu nhập kho từ đơn mua hàng trong một giao dịch duy nhất.
     *
     * <p>Thành công đồng nghĩa: đã ghi phiếu (CONFIRMED), cộng tồn theo variant/kho, ghi thẻ kho
     * và cập nhật tiến độ nhận của đơn mua hàng — tất cả nhất quán hoặc cùng cuộn ngược.</p>
     *
     * @param request     thông tin phiếu và các dòng số lượng thực nhận
     * @param actorUserId người thực hiện (Shop Owner hoặc Accountant); null sẽ lấy từ IAM hiện tại
     * @return phiếu nhập kho đã xác nhận kèm danh sách dòng
     */
    GoodsReceiptResponse createAndConfirm(CreateGoodsReceiptRequest request, UUID actorUserId);

    /** Xem chi tiết một phiếu nhập kho kèm danh sách dòng. */
    GoodsReceiptResponse getDetail(UUID goodsReceiptId);

    /** Danh sách phiếu nhập kho của một đơn mua hàng, mới nhất trước. */
    List<GoodsReceiptResponse> listByPurchaseOrder(UUID purchaseOrderId);

    /** Danh sách phiếu nhập kho phân trang, mới nhất trước. */
    Page<GoodsReceiptResponse> list(Pageable pageable);
}
