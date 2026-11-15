package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.servicedesk.internal.payload.req.CancelWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateWarrantyTicketRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.WarrantyTicketFilterRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.ClaimFormResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyTicketListItemResponse;
import com.aquasuite.ims.module.servicedesk.internal.storage.StoredMedia;
import com.aquasuite.ims.share.dto.PageResponse;
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

