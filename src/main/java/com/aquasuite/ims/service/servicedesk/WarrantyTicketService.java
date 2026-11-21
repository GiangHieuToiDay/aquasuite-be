package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.servicedesk.request.CancelWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyTicketRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyTicketFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.ClaimFormResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyTicketListItemResponse;
import com.aquasuite.ims.dto.servicedesk.response.StoredMedia;
import com.aquasuite.ims.common.PageResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * Dịch vụ xử lý phiếu yêu cầu bảo hành điện tử gửi trực tuyến từ khách hàng (UC-47).
 */
public interface WarrantyTicketService {

    ClaimFormResponse getClaimForm(UUID warrantyRecordId);

    StoredMedia uploadMedia(MultipartFile file);

    WarrantyTicketDetailResponse submit(CreateWarrantyTicketRequest request);

    PageResponse<WarrantyTicketListItemResponse> listMine(WarrantyTicketFilterRequest filter);

    WarrantyTicketDetailResponse getMine(UUID ticketId);

    WarrantyTicketDetailResponse cancel(UUID ticketId, CancelWarrantyTicketRequest request);
}

