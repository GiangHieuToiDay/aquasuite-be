package com.aquasuite.ims.module.servicedesk.internal.payload.res;

import com.aquasuite.ims.module.catalog.api.dto.CatalogBrandSummaryDto;
import com.aquasuite.ims.module.catalog.api.dto.CatalogCategorySummaryDto;
import lombok.Builder;

import java.util.List;

@Builder
public record WarrantyPolicyReferenceDataResponse(List<CatalogBrandSummaryDto> brands,
                                                  List<CatalogCategorySummaryDto> categories) {
}
