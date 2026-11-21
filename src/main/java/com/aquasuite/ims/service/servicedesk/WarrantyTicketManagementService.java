package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.DispatchWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.RejectWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketListItemResponse;
import com.aquasuite.ims.common.PageResponse;

import java.util.UUID;

/**
 * Nghiệp vụ quản lý yêu cầu bảo hành (ticket, UC-47) phía nội bộ cửa hàng (FE-13 Giai đoạn 2b).
 * Chủ cửa hàng/Kỹ thuật viên tra cứu toàn bộ ticket; Chủ cửa hàng "bắn job" sửa chữa bảo hành cho kỹ thuật viên.
 */
public interface WarrantyTicketManagementService {

    /** Danh sách toàn bộ yêu cầu bảo hành (không giới hạn theo customer), có phân trang và lọc theo trạng thái. */
    PageResponse<WarrantyTicketListItemResponse> list(WarrantyTicketFilterRequest filter);

    /** Chi tiết một yêu cầu bảo hành theo id (phạm vi nội bộ, không giới hạn theo customer). */
    WarrantyTicketDetailResponse getById(UUID ticketId);

    /**
     * Chủ cửa hàng "bắn job" sửa chữa bảo hành: tạo công việc kỹ thuật WARRANTY_REPAIR gán kỹ thuật viên,
     * liên kết với ticket và chuyển ticket NEW → IN_PROGRESS.
     */
    WarrantyTicketDetailResponse dispatch(UUID ticketId, DispatchWarrantyTicketRequest request);

    /**
     * Chủ cửa hàng từ chối yêu cầu bảo hành tại bàn (chỉ khi NEW), kèm lý do. Không cử kỹ thuật viên.
     * Dùng cho ca hiển nhiên không đáng cử người: thẻ hết hạn, yêu cầu trùng/rác, ngoài phạm vi bảo hành.
     */
    WarrantyTicketDetailResponse reject(UUID ticketId, RejectWarrantyTicketRequest request);
}
