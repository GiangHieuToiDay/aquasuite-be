package com.aquasuite.ims.module.fulfillment.internal.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

/** Private filesystem storage; HTTP reads remain authorized by DeliveryService. */
public final class LocalDeliveryPhotoStorage implements DeliveryPhotoStorage {
    private final Path root;

    public LocalDeliveryPhotoStorage(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    @Override
    public Stored store(byte[] bytes, String mimeType) {
        String extension = switch (mimeType) {
            case "image/jpeg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new IllegalArgumentException("Unsupported image type");
        };
        String key = UUID.randomUUID() + extension;
        try {
            Files.createDirectories(root);
            Files.write(resolve(key), bytes, StandardOpenOption.CREATE_NEW);
            return new Stored(key);
        } catch (IOException ex) {
            throw new IllegalStateException("Delivery photo storage unavailable", ex);
        }
    }

    @Override
    public Content read(String key) {
        Path file = resolve(key);
        try {
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalStateException("Delivery photo unavailable");
            }
            String mime = key.endsWith(".jpg") ? "image/jpeg"
                    : key.endsWith(".png") ? "image/png" : "image/webp";
            return new Content(Files.readAllBytes(file), mime);
        } catch (IOException ex) {
            throw new IllegalStateException("Delivery photo storage unavailable", ex);
        }
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException ex) {
            throw new IllegalStateException("Delivery photo cleanup failed", ex);
        }
    }

    private Path resolve(String key) {
        if (key == null || !key.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png|webp)")) {
            throw new IllegalArgumentException("Invalid delivery photo key");
        }
        return root.resolve(key);
    }
}
