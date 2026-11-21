package com.aquasuite.ims.controller.fulfillment;

import com.aquasuite.ims.dto.fulfillment.request.DeliveryIssueRequest;
import com.aquasuite.ims.service.fulfillment.DeliveryIssueService;
import com.aquasuite.ims.service.fulfillment.helper.DeliveryIssueNotePdfService;
import com.aquasuite.ims.service.sales.SalesQueryService.InvoiceSummary;
import com.aquasuite.ims.common.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
import java.util.List;

@RestController
@RequestMapping("/api/v1/fulfillment")
@PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER')")
public class DeliveryIssueController {
    private final DeliveryIssueService service;
    private final DeliveryIssueNotePdfService pdfService;

    @Autowired
    public DeliveryIssueController(DeliveryIssueService service, DeliveryIssueNotePdfService pdfService) {
        this.service = service;
        this.pdfService = pdfService;
    }

    public DeliveryIssueController(DeliveryIssueService service) {
        this(service, null);
    }

    @GetMapping("/orders/{orderId}/delivery-invoices")
    public ApiResponse<List<InvoiceSummary>> issuedInvoices(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.issuedInvoices(orderId));
    }

    @GetMapping("/sales-invoices/{invoiceId}/delivery-candidates")
    public ApiResponse<DeliveryIssueService.Candidates> invoiceCandidates(@PathVariable UUID invoiceId) {
        return ApiResponse.ok(service.invoiceCandidates(invoiceId));
    }

    @PostMapping("/sales-invoices/{invoiceId}/deliveries")
    public ApiResponse<DeliveryIssueService.Result> createFromInvoice(@PathVariable UUID invoiceId,
            @Valid @RequestBody DeliveryIssueRequest request) {
        return ApiResponse.ok(service.createFromInvoice(invoiceId, request));
    }

    @GetMapping("/orders/{orderId}/delivery-candidates")
    public ApiResponse<DeliveryIssueService.Candidates> candidates(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.candidates(orderId));
    }

    @PostMapping("/orders/{orderId}/deliveries")
    public ApiResponse<DeliveryIssueService.Result> create(@PathVariable UUID orderId,
            @Valid @RequestBody DeliveryIssueRequest request) {
        return ApiResponse.ok(service.create(orderId, request));
    }

    @PostMapping("/deliveries/{id}/confirm-issue")
    public ApiResponse<DeliveryIssueService.Result> confirm(@PathVariable UUID id) {
        return ApiResponse.ok(service.confirm(id));
    }

    @PutMapping("/deliveries/{id}/draft")
    public ApiResponse<DeliveryIssueService.Result> update(@PathVariable UUID id,
            @Valid @RequestBody DeliveryIssueRequest request) {
        return ApiResponse.ok(service.update(id, request));
    }

    @PostMapping("/deliveries/{id}/cancel-draft")
    public ApiResponse<DeliveryIssueService.Result> cancel(@PathVariable UUID id) {
        return ApiResponse.ok(service.cancel(id));
    }

    // ── from DeliveryIssueNoteController ─────────────────────────────────────

    @GetMapping(value = "/deliveries/{id}/issue-note.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ResponseEntity<byte[]> downloadIssueNote(@PathVariable UUID id) {
        if (pdfService == null) {
            throw new IllegalStateException("DeliveryIssueNotePdfService not configured");
        }
        byte[] pdf = pdfService.generate(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("phieu-xuat-kho-" + id + ".pdf").build().toString())
                .body(pdf);
    }
}
