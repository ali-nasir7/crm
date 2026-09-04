package com.crm.modules.documents.service;

/**
 * Storage abstraction (§21): local filesystem in dev, S3-compatible in production.
 * Keys are generated server-side; downloads are streamed with fixed headers.
 */
public interface StorageProvider {

    String store(String originalFileName, String contentType, byte[] content);

    byte[] load(String storageKey);

    void delete(String storageKey);
}
