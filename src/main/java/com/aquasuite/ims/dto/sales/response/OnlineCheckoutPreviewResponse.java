package com.aquasuite.ims.dto.sales.response;

import com.aquasuite.ims.dto.crm.response.CustomerAddressDto;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Dữ liệu tổng hợp cho màn hình Đặt hàng trực tuyến (UC-18): giỏ hàng + sổ địa chỉ.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OnlineCheckoutPreviewResponse {

    private CartResponse cart;
    private List<CustomerAddressDto> savedAddresses;
    private CustomerAddressDto defaultAddress;
    private BigDecimal shippingFee;
    private BigDecimal grandTotal;
    private boolean payosConfigured;
}

