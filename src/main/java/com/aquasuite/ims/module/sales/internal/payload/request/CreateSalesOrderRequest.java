package com.aquasuite.ims.module.sales.internal.payload.request;

import com.aquasuite.ims.module.sales.internal.enums.FulfillmentMethod;
import com.aquasuite.ims.module.sales.internal.enums.OrderChannel;
import com.aquasuite.ims.module.sales.internal.enums.OrderType;
import com.aquasuite.ims.module.sales.internal.enums.PaymentMethod;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSalesOrderRequest {

    /** ID khách hàng (nếu khách quen đã có trong hệ thống) */
    private UUID customerId;

    /** Tra cứu khách hàng theo SĐT (nếu không có sẽ mặc định Walk-in Customer) */
    private String customerPhone;

    /** Tên khách hàng (tùy chọn lưu kèm nếu là khách vãng lai) */
    private String customerName;

    /** Phân loại đơn hàng: RETAIL (mặc định) hoặc PROJECT */
    @Builder.Default
    private OrderType orderType = OrderType.RETAIL;

    /** Kênh bán hàng: mặc định IN_STORE tại quầy POS */
    @Builder.Default
    private OrderChannel orderChannel = OrderChannel.IN_STORE;

    /** Phương thức nhận hàng: IN_STORE_PICKUP (mặc định) hoặc SCHEDULED_DELIVERY */
    @Builder.Default
    private FulfillmentMethod fulfillmentMethod = FulfillmentMethod.IN_STORE_PICKUP;

    /** Hình thức thanh toán dự kiến: CASH, BANK_TRANSFER, CARD, CREDIT */
    @Builder.Default
    private PaymentMethod paymentMethod = PaymentMethod.CASH;

    /** Chiết khấu hoặc giảm giá theo chương trình khuyến mãi / promo code */
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    /** Địa chỉ giao hàng nếu chọn hình thức SCHEDULED_DELIVERY */
    private String deliveryAddress;

    /** Ghi chú đơn hàng */
    private String note;

    /** Danh sách SKU lẻ */
    @Valid
    @Builder.Default
    private List<CreateOrderItemRequest> items = new ArrayList<>();

    /** Danh sách gói Combo/Bundle */
    @Valid
    @Builder.Default
    private List<CreateComboItemRequest> combos = new ArrayList<>();
}
