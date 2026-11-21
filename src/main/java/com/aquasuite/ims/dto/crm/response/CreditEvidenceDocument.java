package com.aquasuite.ims.dto.crm.response;

/** Private object contents returned only after controller-level authorization. */
public record CreditEvidenceDocument(byte[] content, String contentType, String fileName) {
}
