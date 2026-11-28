package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.dto.catalog.response.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.dto.servicedesk.request.AutoIssueWarrantyCommand;
import com.aquasuite.ims.dto.servicedesk.request.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.dto.servicedesk.request.CreateDeliveryWarrantyCommand;
import com.aquasuite.ims.dto.servicedesk.request.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.dto.servicedesk.request.WarrantyRecordFilterRequest;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyLookupResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyPolicySummaryResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordDetailResponse;
import com.aquasuite.ims.dto.servicedesk.response.WarrantyRecordListItemResponse;
import com.aquasuite.ims.common.PageResponse;

import java.util.List;
import java.util.UUID;

public interface WarrantyRecordService {

    PageResponse<WarrantyRecordListItemResponse> searchRecords(WarrantyRecordFilterRequest filter);

    WarrantyRecordDetailResponse getRecordDetail(UUID warrantyRecordId);

    WarrantyRecordDetailResponse createFromOrder(CreateWarrantyFromOrderRequest request);

    WarrantyRecordDetailResponse createAdHoc(CreateAdHocWarrantyRequest request);

    WarrantyRecordDetailResponse createFromDelivery(CreateDeliveryWarrantyCommand command);

    boolean autoIssueIfEligible(AutoIssueWarrantyCommand command);

    WarrantyRecordDetailResponse voidRecord(UUID warrantyRecordId, String reason);

    PageResponse<WarrantyRecordListItemResponse> getMyRecords(WarrantyRecordFilterRequest filter);

    WarrantyRecordDetailResponse getMyRecordDetail(UUID warrantyRecordId);

    List<WarrantyPolicySummaryResponse> getActivePolicies();

    CatalogWarrantyPolicyDto getVariantWarrantyInfo(UUID variantId);

    boolean isEligible(UUID warrantyRecordId);

    com.aquasuite.ims.dto.servicedesk.response.WarrantyEligibilityDto getEligibility(UUID warrantyRecordId);

    List<WarrantyLookupResponse> findMine(String keyword);
}
