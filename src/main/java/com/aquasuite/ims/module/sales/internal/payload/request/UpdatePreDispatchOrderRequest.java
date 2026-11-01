package com.aquasuite.ims.module.sales.internal.payload.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdatePreDispatchOrderRequest {

    /** Cập nhật địa chỉ nhận hàng */
    private String deliveryAddress;

    /** Cập nhật ghi chú đơn hàng */
    private String note;
}
