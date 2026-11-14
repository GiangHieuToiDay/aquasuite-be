package com.aquasuite.ims.module.sales.internal.controller;

import com.aquasuite.ims.module.sales.internal.payload.request.CreateDraftInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.request.UpdateSalesInvoiceRequest;
import com.aquasuite.ims.module.sales.internal.payload.response.SalesInvoiceResponse;
import com.aquasuite.ims.module.sales.internal.service.SalesInvoiceService;
import com.aquasuite.ims.share.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * REST Controller quản lý Hóa đơn bán hàng (DRAFT -> ISSUED / CANCELLED) gắn với đơn hàng.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
public class SalesInvoiceController {

    private final SalesInvoiceService salesInvoiceService;

    @GetMapping("/orders/{orderId}/invoices")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER', 'ACCOUNTANT', 'SHIPPER', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<List<SalesInvoiceResponse>>> getOrderInvoices(
            @PathVariable UUID orderId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(salesInvoiceService.getInvoicesByOrderId(orderId)));
    }

    @PostMapping("/orders/{orderId}/invoices")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<SalesInvoiceResponse>> createDraftInvoice(
            @PathVariable UUID orderId,
            @Valid @RequestBody CreateDraftInvoiceRequest request
    ) {
        log.info("API: Create DRAFT invoice batch for orderId={}", orderId);
        SalesInvoiceResponse created = salesInvoiceService.createDraftInvoice(orderId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok(created, "Tạo hóa đơn nháp cho đợt giao mới thành công."));
    }

    @GetMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER', 'ACCOUNTANT', 'SHIPPER', 'TECHNICAL_STAFF')")
    public ResponseEntity<ApiResponse<SalesInvoiceResponse>> getInvoiceDetail(
            @PathVariable UUID invoiceId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(salesInvoiceService.getInvoiceDetail(invoiceId)));
    }

    @PutMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<SalesInvoiceResponse>> updateDraftInvoice(
            @PathVariable UUID invoiceId,
            @Valid @RequestBody UpdateSalesInvoiceRequest request
    ) {
        log.info("API: Update DRAFT invoiceId={}", invoiceId);
        SalesInvoiceResponse updated = salesInvoiceService.updateDraftInvoice(invoiceId, request);
        return ResponseEntity.ok(ApiResponse.ok(updated, "Cập nhật hóa đơn nháp thành công."));
    }

    @PostMapping("/invoices/{invoiceId}/issue")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<SalesInvoiceResponse>> issueInvoice(
            @PathVariable UUID invoiceId
    ) {
        log.info("API: Issue invoiceId={}", invoiceId);
        SalesInvoiceResponse issued = salesInvoiceService.issueInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(issued, "Chốt và phát hành hóa đơn thành công."));
    }

    @DeleteMapping("/invoices/{invoiceId}")
    @PreAuthorize("hasAnyRole('SALES_STAFF', 'SHOP_OWNER')")
    public ResponseEntity<ApiResponse<Void>> cancelDraftInvoice(
            @PathVariable UUID invoiceId
    ) {
        log.info("API: Cancel DRAFT invoiceId={}", invoiceId);
        salesInvoiceService.cancelDraftInvoice(invoiceId);
        return ResponseEntity.ok(ApiResponse.ok(null, "Đã hủy hóa đơn nháp thành công."));
    }
}

