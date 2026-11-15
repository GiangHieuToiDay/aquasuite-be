package com.aquasuite.ims.module.sales.internal.service;

import com.aquasuite.ims.module.sales.internal.payload.request.CreateDraftInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateSalesInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesInvoiceResponse;

import java.util.List;
import java.util.UUID;

/** Quản lý vòng đời hóa đơn bán hàng (DRAFT -> ISSUED / CANCELLED) gắn với đơn hàng. */
public interface SalesInvoiceService {

    List<SalesInvoiceResponse> getInvoicesByOrderId(UUID orderId);

    SalesInvoiceResponse getInvoiceDetail(UUID invoiceId);

    SalesInvoiceResponse createDraftInvoice(UUID orderId, CreateDraftInvoiceRequest request);

    SalesInvoiceResponse updateDraftInvoice(UUID invoiceId, UpdateSalesInvoiceRequest request);

    SalesInvoiceResponse issueInvoice(UUID invoiceId);

    void cancelDraftInvoice(UUID invoiceId);
}

