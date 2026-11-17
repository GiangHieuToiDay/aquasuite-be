package com.aquasuite.ims.module.crm.internal.storage;

/** Private object contents returned only after controller-level authorization. */
public record CreditEvidenceDocument(byte[] content, String contentType, String fileName) {
}
