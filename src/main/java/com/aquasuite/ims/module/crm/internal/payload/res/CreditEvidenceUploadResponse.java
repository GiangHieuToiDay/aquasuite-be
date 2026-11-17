package com.aquasuite.ims.module.crm.internal.payload.res;

/** Opaque private MinIO object key, consumed only when the request is submitted. */
public record CreditEvidenceUploadResponse(String identityDocumentObjectKey) {
}
