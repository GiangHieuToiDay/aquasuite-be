package com.aquasuite.ims.module.purchasing.internal.service;

import com.aquasuite.ims.module.purchasing.internal.payload.request.CancelPurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.ClosePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.CreatePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.PurchaseOrderFilterRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.request.UpdatePurchaseOrderRequest;
import com.aquasuite.ims.module.purchasing.internal.payload.response.LowStockSuggestionResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderListItemResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.PurchaseOrderResponse;
import com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierItemOptionResponse;
import com.aquasuite.ims.share.dto.PageResponse;

import java.util.List;
import java.util.UUID;

public interface PurchaseOrderService {

    PurchaseOrderResponse createDraft(CreatePurchaseOrderRequest request);

    PurchaseOrderResponse getDetail(UUID purchaseOrderId);

    PurchaseOrderResponse updateDraft(UUID purchaseOrderId, UpdatePurchaseOrderRequest request);

    PageResponse<PurchaseOrderListItemResponse> listPurchaseOrders(PurchaseOrderFilterRequest filter);

    PurchaseOrderResponse confirmPurchaseOrder(UUID purchaseOrderId);

    PurchaseOrderResponse reopenToQuoted(UUID purchaseOrderId, com.aquasuite.ims.module.purchasing.internal.payload.request.ReopenPoRequest request);

    PurchaseOrderResponse settleStage(UUID purchaseOrderId, UUID stageId, com.aquasuite.ims.module.purchasing.internal.payload.request.SettleStageRequest request);

    com.aquasuite.ims.module.purchasing.internal.payload.response.StageRollbackRequestResponse requestStageRollback(UUID purchaseOrderId, UUID stageId, com.aquasuite.ims.module.purchasing.internal.payload.request.StageRollbackRequest request);

    PurchaseOrderResponse reviewStageRollback(UUID purchaseOrderId, UUID rollbackId, com.aquasuite.ims.module.purchasing.internal.payload.request.ReviewRollbackRequest request);

    PurchaseOrderResponse directRollbackStage(UUID purchaseOrderId, UUID stageId, com.aquasuite.ims.module.purchasing.internal.payload.request.StageRollbackRequest request);

    PurchaseOrderResponse closeShortPurchaseOrder(UUID purchaseOrderId, com.aquasuite.ims.module.purchasing.internal.payload.request.CloseShortPoRequest request);

    PurchaseOrderResponse cancelPurchaseOrder(UUID purchaseOrderId, CancelPurchaseOrderRequest request);

    PurchaseOrderResponse closePurchaseOrder(UUID purchaseOrderId, ClosePurchaseOrderRequest request);

    byte[] exportPdf(UUID purchaseOrderId);

    List<LowStockSuggestionResponse> getLowStockSuggestions(UUID supplierId);

    List<SupplierItemOptionResponse> getSupplierItems(UUID supplierId, boolean all);

    List<com.aquasuite.ims.module.purchasing.internal.payload.response.SupplierOptionResponse> getActiveSuppliers();
}