package com.aquasuite.ims.module.fulfillment.internal.storage;

/** Private photo storage with local filesystem default and replaceable external adapters. */
public interface DeliveryPhotoStorage {
    Stored store(byte[] bytes, String mimeType);
    Content read(String storageKey);
    void delete(String storageKey);
    record Stored(String key) {}
    record Content(byte[] bytes, String mimeType) {}
}
