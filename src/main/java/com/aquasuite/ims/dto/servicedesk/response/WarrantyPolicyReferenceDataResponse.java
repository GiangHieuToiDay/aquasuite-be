package com.aquasuite.ims.dto.servicedesk.response;

import com.aquasuite.ims.dto.catalog.response.CatalogBrandSummaryDto;
import com.aquasuite.ims.dto.catalog.response.CatalogCategorySummaryDto;
import lombok.Builder;

import java.util.List;

@Builder
public record WarrantyPolicyReferenceDataResponse(List<CatalogBrandSummaryDto> brands,
                                                  List<CatalogCategorySummaryDto> categories) {
}
