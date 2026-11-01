package com.aquasuite.ims.module.sales.internal.mapper;

import com.aquasuite.ims.module.sales.internal.entity.Order;
import com.aquasuite.ims.module.sales.internal.entity.OrderItem;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderDetailResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderItemResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesOrderSummaryResponse;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesInvoiceSummaryResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct Mapper cho Đơn hàng bán lẻ (Sales Order) và các dòng sản phẩm chi tiết.
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
    SalesOrderSummaryResponse toSummaryResponse(Order order, int itemCount, String customerName, String customerPhone, String salesStaffName);

    SalesInvoiceSummaryResponse toInvoiceSummary(com.aquasuite.ims.module.sales.internal.entity.SalesInvoice invoice);

    @Mapping(target = "items", source = "items")
    @Mapping(target = "customerName", source = "customerName")
    @Mapping(target = "customerPhone", source = "customerPhone")
    @Mapping(target = "salesStaffName", source = "salesStaffName")
    @Mapping(target = "paidAmount", source = "paidAmount")
    @Mapping(target = "paymentTransactions", source = "paymentTransactions")
    SalesOrderDetailResponse toDetailResponse(
            Order order,
            List<SalesOrderItemResponse> items,
            String customerName,
            String customerPhone,
            String salesStaffName,
            java.math.BigDecimal paidAmount,
            List<com.aquasuite.ims.module.finance.api.dto.PaymentTransactionResponseDto> paymentTransactions
    );
}
