package com.aquasuite.ims.module.sales.internal.mapper;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct Mapper cho Đơn hàng bán lẻ (Sales Order - UC-21) và các dòng sản phẩm chi tiết.
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface SalesOrderMapper {

    SalesOrderItemResponse toItemResponse(OrderItem item);

    List<SalesOrderItemResponse> toItemResponseList(List<OrderItem> items);

    @Mapping(target = "itemCount", source = "itemCount")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "customerPhone", source = "customerPhone")
    @Mapping(target = "salesStaffName", source = "salesStaffName")
    SalesOrderSummaryResponse toSummaryResponse(
            Order order,
            int itemCount,
            String customerName,
            String customerPhone,
            String salesStaffName
    );

    @Mapping(target = "items", source = "items")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "customerPhone", source = "customerPhone")
    @Mapping(target = "salesStaffName", source = "salesStaffName")
    SalesOrderDetailResponse toDetailResponse(
            Order order,
            List<SalesOrderItemResponse> items,
            String customerName,
            String customerPhone,
            String salesStaffName
    );
}
