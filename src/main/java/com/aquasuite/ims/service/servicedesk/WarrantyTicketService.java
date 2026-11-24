package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.CancelWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CompleteWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.RejectWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.ClaimFormResponse;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketListItemResponse;
import com.aquasuite.ims.common.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Dịch vụ xử lý phiếu yêu cầu bảo hành điện tử (UC-47) cho cả khách hàng và quản lý nội bộ.
 */
public interface WarrantyTicketService {

    ClaimFormResponse getClaimForm(UUID warrantyRecordId);

    StoredMedia uploadMedia(MultipartFile file);

    WarrantyTicketDetailResponse submit(CreateWarrantyTicketRequest request);

    PageResponse<WarrantyTicketListItemResponse> listMine(WarrantyTicketFilterRequest filter);

    WarrantyTicketDetailResponse getMine(UUID ticketId);

    WarrantyTicketDetailResponse cancel(UUID ticketId, CancelWarrantyTicketRequest request);

    /** Danh sách toàn bộ yêu cầu bảo hành (không giới hạn theo customer), có phân trang và lọc theo trạng thái. */
    PageResponse<WarrantyTicketListItemResponse> list(WarrantyTicketFilterRequest filter);

    /** Chi tiết một yêu cầu bảo hành theo id (phạm vi nội bộ, không giới hạn theo customer). */
    WarrantyTicketDetailResponse getById(UUID ticketId);

    /** Chủ cửa hàng tiếp nhận xử lý yêu cầu bảo hành: NEW → IN_PROGRESS. */
    WarrantyTicketDetailResponse startProcessing(UUID ticketId);

    /**
     * Chủ cửa hàng hoàn tất yêu cầu bảo hành: IN_PROGRESS → DONE, bắt buộc ghi kết quả
     * (đã sửa miễn phí / có phí, hoặc không bảo hành vì ... khi phát hiện trong lúc xử lý).
     */
    WarrantyTicketDetailResponse complete(UUID ticketId, CompleteWarrantyTicketRequest request);

    /**
     * Chủ cửa hàng từ chối yêu cầu bảo hành tại bàn (chỉ khi NEW), kèm lý do.
     */
    WarrantyTicketDetailResponse reject(UUID ticketId, RejectWarrantyTicketRequest request);
}
