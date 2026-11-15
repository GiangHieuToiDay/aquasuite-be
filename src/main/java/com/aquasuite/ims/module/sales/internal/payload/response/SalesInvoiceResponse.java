package com.aquasuite.ims.module.sales.internal.payload.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** DTO trả về chi tiết hóa đơn của đơn hàng cho trang OrderDetailPage & Popup Hóa đơn. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SalesInvoiceResponse {

    private UUID invoiceId;
    private String invoiceCode;
    private UUID orderId;
    private String orderCode;
    private UUID customerId;
    private String invoiceType;
    private String status;
    private Integer batchNo;
    private String batchName;
    private OffsetDateTime scheduledDeliveryDate;
    private OffsetDateTime issueDate;
    private Integer creditTermDays;
    private LocalDate dueDate;
    private BigDecimal subtotal;
    private BigDecimal discountAmount;
    private BigDecimal taxAmount;
    private BigDecimal totalAmount;
    private UUID createdBy;
    private OffsetDateTime createdAt;
    private UUID issuedBy;
    private OffsetDateTime issuedAt;
    private UUID cancelledBy;
    private OffsetDateTime cancelledAt;
    private String note;

    /** Cho biết hóa đơn có đang cho phép chỉnh sửa / phát hành trên UI hay không. */
    private boolean editable;

    /** Cờ báo đơn hàng hiện có yêu cầu UC-26 chờ xử lý làm khóa sửa hóa đơn. */
    private boolean lockedByModificationRequest;

    @Builder.Default
    private List<SalesInvoiceItemResponse> items = new ArrayList<>();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SalesInvoiceItemResponse {
        private UUID invoiceItemId;
        private UUID orderItemId;
        private UUID variantId;
        private String sku;
        private String description;
        private BigDecimal quantity;
        private BigDecimal unitPrice;
        private BigDecimal discountAmount;
        private BigDecimal taxRate;
        private BigDecimal lineTotal;
    }
}

