package com.aquasuite.ims.module.sales.internal.mapper;

import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequest;
import com.aquasuite.ims.module.sales.internal.entity.OrderModificationRequestItem;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.OrderModificationRequestResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/** Structural mapping only; eligibility and state transitions remain in the UC-26 service. */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface OrderModificationRequestMapper {

    @Mapping(source = "modificationRequestId", target = "id")
    @Mapping(target = "items", ignore = true)
    OrderModificationRequestResponse toResponse(OrderModificationRequest request);

    @Mapping(source = "modificationRequestItemId", target = "id")
    OrderModificationRequestItemResponse toItemResponse(OrderModificationRequestItem item);

    List<OrderModificationRequestItemResponse> toItemResponses(List<OrderModificationRequestItem> items);
}
