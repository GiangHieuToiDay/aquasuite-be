package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.CancelWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.DispatchWarrantyTicketRequest;
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

    /**
     * Chủ cửa hàng "bắn job" sửa chữa bảo hành: tạo công việc kỹ thuật WARRANTY_REPAIR gán kỹ thuật viên,
     * liên kết với ticket và chuyển ticket NEW → IN_PROGRESS.
     */
    WarrantyTicketDetailResponse dispatch(UUID ticketId, DispatchWarrantyTicketRequest request);

    /**
     * Chủ cửa hàng từ chối yêu cầu bảo hành tại bàn (chỉ khi NEW), kèm lý do. Không cử kỹ thuật viên.
     */
    WarrantyTicketDetailResponse reject(UUID ticketId, RejectWarrantyTicketRequest request);
}
