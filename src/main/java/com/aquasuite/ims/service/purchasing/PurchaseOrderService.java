package com.aquasuite.ims.service.purchasing;

import com.aquasuite.ims.dto.purchasing.request.CancelPurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.ClosePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.CreatePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.request.PurchaseOrderFilterRequest;
import com.aquasuite.ims.dto.purchasing.request.UpdatePurchaseOrderRequest;
import com.aquasuite.ims.dto.purchasing.response.LowStockSuggestionResponse;
import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderListItemResponse;
import com.aquasuite.ims.dto.purchasing.response.PurchaseOrderResponse;
import com.aquasuite.ims.dto.purchasing.response.SupplierItemOptionResponse;
import com.aquasuite.ims.common.PageResponse;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderService {

    PurchaseOrderResponse createDraft(CreatePurchaseOrderRequest request);

    PurchaseOrderResponse getDetail(UUID purchaseOrderId);

    PurchaseOrderResponse updateDraft(UUID purchaseOrderId, UpdatePurchaseOrderRequest request);

    PageResponse<PurchaseOrderListItemResponse> listPurchaseOrders(PurchaseOrderFilterRequest filter);

    PurchaseOrderResponse confirmPurchaseOrder(UUID purchaseOrderId);

    PurchaseOrderResponse cancelPurchaseOrder(UUID purchaseOrderId, CancelPurchaseOrderRequest request);

    PurchaseOrderResponse closePurchaseOrder(UUID purchaseOrderId, ClosePurchaseOrderRequest request);

    byte[] exportPdf(UUID purchaseOrderId);

    List<LowStockSuggestionResponse> getLowStockSuggestions(UUID supplierId);

    List<SupplierItemOptionResponse> getSupplierItems(UUID supplierId, boolean all);

    List<com.aquasuite.ims.dto.purchasing.response.SupplierOptionResponse> getActiveSuppliers();

    java.math.BigDecimal getIncomingStockQty(java.util.UUID variantId);

    java.util.Map<java.util.UUID, java.math.BigDecimal> getIncomingStockQtyMap(java.util.Collection<java.util.UUID> variantIds);

    java.util.Optional<com.aquasuite.ims.dto.purchasing.response.ReceivablePurchaseOrderView> getReceivablePurchaseOrder(java.util.UUID purchaseOrderId);

    /**
     * UC-32: khóa dòng PO (FOR UPDATE) trong transaction hiện tại của bên gọi và trả về snapshot mới nhất
     * (trạng thái, số đã nhận, số còn lại). Khóa được giữ đến khi transaction của bên gọi kết thúc, nên
     * việc validate số còn lại, ghi phiếu/tồn và {@link #applyGoodsReceipt} không bị xen bởi phiếu nhập,
     * hủy hoặc đóng PO khác.
     *
     * <p>Transaction: {@code MANDATORY} — phải được gọi bên trong transaction ghi của bên nhập kho.</p>
     *
     * @return snapshot đã khóa; rỗng nếu không tồn tại PO
     */
    java.util.Optional<com.aquasuite.ims.dto.purchasing.response.ReceivablePurchaseOrderView> lockReceivablePurchaseOrder(java.util.UUID purchaseOrderId);

    /**
     * UC-32: cộng dồn số đã nhận theo từng dòng PO và tính trạng thái cuối
     * ({@code PARTIALLY_RECEIVED} hoặc {@code COMPLETED}). Không bao giờ bỏ qua im lặng: nếu PO không còn
     * nhận được, dòng không thuộc PO, số không dương hoặc vượt số còn lại thì ném {@link com.aquasuite.ims.exception.AppException}
     * để transaction của bên gọi cuộn ngược toàn bộ (phiếu, tồn, thẻ kho).
     *
     * <p>Transaction: {@code MANDATORY}; tự khóa lại dòng PO (tái nhập được nếu bên gọi đã khóa).</p>
     *
     * @param receivedByPurchaseOrderItemId số nhận lần này theo {@code purchaseOrderItemId}
     * @return trạng thái PO sau khi cập nhật
     * @throws com.aquasuite.ims.exception.AppException PO_001, PO_021 (409), PO_022, PO_020
     */
    String applyGoodsReceipt(java.util.UUID purchaseOrderId, java.util.Map<java.util.UUID, java.math.BigDecimal> receivedByPurchaseOrderItemId);
}
