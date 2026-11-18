package com.aquasuite.ims.module.servicedesk.internal.service;

import com.aquasuite.ims.module.catalog.api.dto.CatalogWarrantyPolicyDto;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateAdHocWarrantyRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateDeliveryWarrantyCommand;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.CreateWarrantyFromOrderRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.req.WarrantyRecordFilterRequest;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyPolicySummaryResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyRecordDetailResponse;
import com.aquasuite.ims.module.servicedesk.internal.payload.res.WarrantyRecordListItemResponse;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface WarrantyRecordService {

    PageResponse<WarrantyRecordListItemResponse> searchRecords(WarrantyRecordFilterRequest filter);

    WarrantyRecordDetailResponse getRecordDetail(UUID warrantyRecordId);

    WarrantyRecordDetailResponse createFromOrder(CreateWarrantyFromOrderRequest request);

    WarrantyRecordDetailResponse createAdHoc(CreateAdHocWarrantyRequest request);

    WarrantyRecordDetailResponse createFromDelivery(CreateDeliveryWarrantyCommand command);

    WarrantyRecordDetailResponse voidRecord(UUID warrantyRecordId, String reason);

    PageResponse<WarrantyRecordListItemResponse> getMyRecords(WarrantyRecordFilterRequest filter);

    WarrantyRecordDetailResponse getMyRecordDetail(UUID warrantyRecordId);

    List<WarrantyPolicySummaryResponse> getActivePolicies();

    CatalogWarrantyPolicyDto getVariantWarrantyInfo(UUID variantId);
}
