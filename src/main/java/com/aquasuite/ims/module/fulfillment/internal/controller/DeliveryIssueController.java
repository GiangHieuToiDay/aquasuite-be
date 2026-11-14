package com.aquasuite.ims.module.fulfillment.internal.controller;

import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryIssueRequest;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryIssueService;
import com.aquasuite.ims.module.sales.api.OrderDispatchApi.InvoiceSummary;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fulfillment")
@PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
@RequiredArgsConstructor
public class DeliveryIssueController {
    private final DeliveryIssueService service;
    @GetMapping("/orders/{orderId}/delivery-invoices")
    public ApiResponse<List<InvoiceSummary>> issuedInvoices(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.issuedInvoices(orderId));
    }
    @GetMapping("/sales-invoices/{invoiceId}/delivery-candidates")
    public ApiResponse<DeliveryIssueService.Candidates> invoiceCandidates(@PathVariable UUID invoiceId) { return ApiResponse.ok(service.invoiceCandidates(invoiceId)); }
    @PostMapping("/sales-invoices/{invoiceId}/deliveries")
    public ApiResponse<DeliveryIssueService.Result> createFromInvoice(@PathVariable UUID invoiceId, @Valid @RequestBody DeliveryIssueRequest request) { return ApiResponse.ok(service.createFromInvoice(invoiceId,request)); }
    @GetMapping("/orders/{orderId}/delivery-candidates")
    public ApiResponse<DeliveryIssueService.Candidates> candidates(@PathVariable UUID orderId) { return ApiResponse.ok(service.candidates(orderId)); }
    @PostMapping("/orders/{orderId}/deliveries")
    public ApiResponse<DeliveryIssueService.Result> create(@PathVariable UUID orderId, @Valid @RequestBody DeliveryIssueRequest request) { return ApiResponse.ok(service.create(orderId,request)); }
    @PostMapping("/deliveries/{id}/confirm-issue")
    public ApiResponse<DeliveryIssueService.Result> confirm(@PathVariable UUID id) { return ApiResponse.ok(service.confirm(id)); }
    @PostMapping("/deliveries/{id}/cancel-draft")
    public ApiResponse<DeliveryIssueService.Result> cancel(@PathVariable UUID id) { return ApiResponse.ok(service.cancel(id)); }
}
