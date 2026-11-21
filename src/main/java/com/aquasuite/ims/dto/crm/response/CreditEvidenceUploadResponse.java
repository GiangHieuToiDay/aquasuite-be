package com.aquasuite.ims.dto.crm.response;

/** Opaque private MinIO object key, consumed only when the request is submitted. */
public record CreditEvidenceUploadResponse(String identityDocumentObjectKey) {
}
