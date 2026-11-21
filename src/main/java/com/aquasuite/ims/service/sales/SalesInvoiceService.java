package com.aquasuite.ims.service.sales;

import com.aquasuite.ims.dto.sales.request.CreateDraftInvoiceRequest;
import com.aquasuite.ims.dto.sales.request.UpdateSalesInvoiceRequest;
import com.aquasuite.ims.dto.sales.request.LinkElectronicInvoiceRequest;
import com.aquasuite.ims.dto.sales.response.SalesInvoiceResponse;

import java.util.List;
import java.util.UUID;

/** Quản lý vòng đời hóa đơn bán hàng (DRAFT -> ISSUED / CANCELLED) gắn với đơn hàng. */
public interface SalesInvoiceService {

    List<SalesInvoiceResponse> getInvoicesByOrderId(UUID orderId);

    SalesInvoiceResponse getInvoiceDetail(UUID invoiceId);

    SalesInvoiceResponse createDraftInvoice(UUID orderId, CreateDraftInvoiceRequest request);

    SalesInvoiceResponse updateDraftInvoice(UUID invoiceId, UpdateSalesInvoiceRequest request);

    SalesInvoiceResponse issueInvoice(UUID invoiceId);

    SalesInvoiceResponse linkElectronicInvoice(UUID invoiceId, LinkElectronicInvoiceRequest request);

    void cancelDraftInvoice(UUID invoiceId);

    java.util.Optional<com.aquasuite.ims.dto.sales.response.SalesInvoiceSummaryDto> getInvoiceById(UUID invoiceId);

    java.util.List<com.aquasuite.ims.dto.sales.response.SalesInvoiceSummaryDto> getIssuedInvoicesByOrderId(UUID orderId);

    boolean areAllActiveInvoicesIssued(UUID orderId);

    void completeOrderIfEligibleAfterDispatch(UUID orderId);
}
