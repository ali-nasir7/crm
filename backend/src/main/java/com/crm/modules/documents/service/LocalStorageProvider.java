package com.crm.modules.documents.service;

import com.crm.common.api.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.UUID;

@Component
public class LocalStorageProvider implements StorageProvider {

    private final Path root;

    public LocalStorageProvider(@Value("${crm.app.storage-dir:./data/storage}") String dir) {
        this.root = Paths.get(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create storage directory: " + root, e);
        }
    }

    @Override
    public String store(String originalFileName, String contentType, byte[] content) {
        String ext = originalFileName != null && originalFileName.contains(".")
            ? originalFileName.substring(originalFileName.lastIndexOf('.')) : "";
        String key = java.time.LocalDate.now() + "/" + UUID.randomUUID() + ext;
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
            return key;
        } catch (IOException e) {
            throw new IllegalStateException("Storage write failed", e);
        }
    }

    @Override
    public byte[] load(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException e) {
            throw ApiException.notFound("File not found");
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException ignored) {
        }
    }

    /** Path traversal guard. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) throw ApiException.badRequest("Invalid storage key");
        return p;
    }
}
