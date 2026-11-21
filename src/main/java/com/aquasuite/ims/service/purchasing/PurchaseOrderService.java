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

    void onGoodsReceiptConfirmed(java.util.UUID purchaseOrderId, java.util.Map<java.util.UUID, java.math.BigDecimal> receivedItems);
}
