package com.aquasuite.ims.mapper.sales;

import com.aquasuite.ims.entity.sales.OnlinePayosPayment;
import com.aquasuite.ims.entity.sales.Order;
import com.aquasuite.ims.dto.sales.response.OnlineOrderCheckoutResponse;
import com.aquasuite.ims.dto.sales.response.PayosPaymentLinkResponse;
import com.aquasuite.ims.dto.sales.response.SalesOrderItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.ReportingPolicy;

import java.util.List;

/**
 * MapStruct Mapper chuyển đổi dữ liệu Đặt hàng & Thanh toán PayOS trực tuyến (UC-18).
 */
@Mapper(
        componentModel = MappingConstants.ComponentModel.SPRING,
        unmappedTargetPolicy = ReportingPolicy.ERROR
)
public interface OnlineCheckoutMapper {

    PayosPaymentLinkResponse toPayosPaymentLinkResponse(OnlinePayosPayment payment);

    @Mapping(target = "orderId", source = "order.orderId")
    @Mapping(target = "orderCode", source = "order.orderCode")
    @Mapping(target = "customerId", source = "order.customerId")
    @Mapping(target = "orderChannel", source = "order.orderChannel")
    @Mapping(target = "orderType", source = "order.orderType")
    @Mapping(target = "orderStatus", source = "order.orderStatus")
    @Mapping(target = "paymentStatus", source = "order.paymentStatus")
    @Mapping(target = "paymentMethod", source = "order.paymentMethod")
    @Mapping(target = "subtotal", source = "order.subtotal")
    @Mapping(target = "shippingFee", source = "order.shippingFee")
    @Mapping(target = "totalAmount", source = "order.totalAmount")
    @Mapping(target = "deliveryAddress", source = "order.deliveryAddress")
    @Mapping(target = "deliverySchedule", source = "order.deliverySchedule")
    @Mapping(target = "note", source = "order.note")
    @Mapping(target = "createdAt", source = "order.createdAt")
    @Mapping(target = "items", source = "items")
    @Mapping(target = "payosPayment", source = "payosPayment")
    OnlineOrderCheckoutResponse toOrderCheckoutResponse(
            Order order,
            List<SalesOrderItemResponse> items,
            PayosPaymentLinkResponse payosPayment
    );
}

