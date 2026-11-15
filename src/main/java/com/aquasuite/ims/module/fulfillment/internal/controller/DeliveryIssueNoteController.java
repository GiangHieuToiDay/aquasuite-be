package com.aquasuite.ims.module.fulfillment.internal.controller;

import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryIssueNotePdfService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fulfillment/deliveries")
@RequiredArgsConstructor
public class DeliveryIssueNoteController {
    private final DeliveryIssueNotePdfService service;

    @GetMapping(value = "/{id}/issue-note.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    @PreAuthorize("hasAnyRole('ACCOUNTANT','SHOP_OWNER','SHIPPER')")
    public ResponseEntity<byte[]> download(@PathVariable UUID id) {
        byte[] pdf = service.generate(id);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_PDF)
                .cacheControl(CacheControl.noStore()).header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("phieu-xuat-kho-" + id + ".pdf").build().toString()).body(pdf);
    }
}
