package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.ApproveQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkAcceptedRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.MarkLostRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationFilterRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.QuotationUpsertRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.RejectQuotationRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.SubmitApprovalRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationListItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.QuotationResponse;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface QuotationService {

    // =========================================================================
    // NHÓM 2: SOẠN THẢO, TÍNH NHẨM & LƯU NHÁP (DRAFTING & CALCULATION)
    // =========================================================================

    /**
     * Tạo mới một báo giá dự án ở trạng thái bản nháp (DRAFT - Rev 1).
     *
     * @param request thông tin yêu cầu tạo báo giá từ client
     * @return chi tiết báo giá đã tạo kèm tính toán tài chính
     */
    QuotationResponse createQuotation(QuotationUpsertRequest request);

    /**
     * Tính nhẩm tài chính trực tiếp trên RAM phục vụ realtime form (không lưu DB).
     *
     * @param request dữ liệu các dòng items, vatRate, shippingFee
     * @return kết quả tính toán chi tiết và cờ cảnh báo duyệt giá
     */
    QuotationResponse previewCalculation(QuotationUpsertRequest request);

    /**
     * Cập nhật thông tin báo giá bản nháp (AF2).
     *
     * @param quotationId ID báo giá cần cập nhật
     * @param request     dữ liệu cập nhật
     * @return thông tin chi tiết báo giá sau khi cập nhật
     */
    QuotationResponse updateQuotation(UUID quotationId, QuotationUpsertRequest request);

    /**
     * Xóa báo giá bản gốc đang ở trạng thái nháp (Rev 1).
     *
     * @param quotationId ID báo giá cần xóa
     */
    void deleteQuotation(UUID quotationId);

    // =========================================================================
    // NHÓM 3: LUỒNG PHÊ DUYỆT CHIẾT KHẤU VƯỢT KHUNG (APPROVAL WORKFLOW - AF1)
    // =========================================================================

    /**
     * Trình sếp duyệt báo giá khi chiết khấu vượt khung (AF1 Step 2).
     *
     * @param quotationId ID báo giá cần trình duyệt
     * @param request     lý do giải trình vượt khung
     * @return báo giá ở trạng thái PENDING_APPROVAL
     */
    QuotationResponse submitForApproval(UUID quotationId, SubmitApprovalRequest request);

    /**
     * Chủ cửa hàng phê duyệt báo giá (AF1 Step 3a).
     *
     * @param quotationId ID báo giá cần duyệt
     * @param request     ghi chú duyệt bổ sung (nếu có)
     * @return báo giá ở trạng thái APPROVED
     */
    QuotationResponse approveQuotation(UUID quotationId, ApproveQuotationRequest request);

    /**
     * Chủ cửa hàng từ chối báo giá (AF1 Step 3b).
     *
     * @param quotationId ID báo giá bị từ chối
     * @param request     lý do từ chối
     * @return báo giá ở trạng thái REJECTED
     */
    QuotationResponse rejectQuotation(UUID quotationId, RejectQuotationRequest request);

    // =========================================================================
    // NHÓM 5: QUẢN LÝ PHIÊN BẢN & KẾT QUẢ ĐÀM PHÁN (POST-SENT APIs - AF3 & AF5)
    // =========================================================================

    /**
     * Phát hành / Gửi báo giá cho khách hàng (chuyển sang trạng thái SENT).
     *
     * @param quotationId ID báo giá cần phát hành
     * @return báo giá ở trạng thái SENT
     */
    QuotationResponse sendQuotation(UUID quotationId);

    /**
     * Tạo phiên bản mới (Revision) từ báo giá đã gửi (AF3).
     *
     * @param quotationId ID báo giá gốc đang ở trạng thái SENT
     * @return bản Revision mới ở trạng thái DRAFT với giá và tồn kho cập nhật mới nhất
     */
    QuotationResponse createRevision(UUID quotationId);

    /**
     * Chốt đàm phán thành công (AF5 Step 1).
     *
     * @param quotationId ID báo giá đang ở trạng thái SENT
     * @param request     file hợp đồng/chữ ký đính kèm
     * @return báo giá ở trạng thái ACCEPTED
     */
    QuotationResponse markAccepted(UUID quotationId, MarkAcceptedRequest request);

    /**
     * Đánh dấu đàm phán thất bại / mất dự án (AF5 Step 2).
     *
     * @param quotationId ID báo giá đang ở trạng thái SENT
     * @param request     lý do thất bại
     * @return báo giá ở trạng thái LOST
     */
    QuotationResponse markLost(UUID quotationId, MarkLostRequest request);

    // =========================================================================
    // TRA CỨU & TRUY VẤN (READ / QUERY APIs)
    // =========================================================================

    /**
     * Lấy thông tin chi tiết một báo giá (phục vụ xem chi tiết & thẩm định trước khi duyệt).
     * Tự động ẩn ownerData đối với nhân viên thường, chỉ hiển thị cho Quản trị viên/Chủ cửa hàng.
     *
     * @param quotationId ID báo giá
     * @return chi tiết báo giá
     */
    QuotationResponse getQuotationById(UUID quotationId);

    /**
     * Lấy danh sách toàn bộ các phiên bản (Revision History) của một mã báo giá.
     *
     * @param quotationCode Mã báo giá (ví dụ: QT-202610-0001)
     * @return danh sách các phiên bản sắp xếp giảm dần theo revisionNo
     */
    List<QuotationListItemResponse> getRevisionHistory(String quotationCode);

    /**
     * Tra cứu danh sách báo giá có phân trang và bộ lọc linh hoạt.
     * Tự động giới hạn dữ liệu: Sales Staff chỉ xem được báo giá của mình, Admin xem được toàn bộ.
     *
     * @param filterRequest bộ lọc và tham số phân trang
     * @return trang danh sách báo giá
     */
    PageResponse<QuotationListItemResponse> listQuotations(QuotationFilterRequest filterRequest);
}
