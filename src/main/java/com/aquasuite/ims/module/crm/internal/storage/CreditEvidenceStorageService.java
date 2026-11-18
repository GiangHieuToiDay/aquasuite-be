package com.aquasuite.ims.module.crm.internal.storage;

import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreditEvidenceStorageService {
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/jpg", "image/png", "image/webp");

    @Value("${minio.endpoint:}") private String endpoint;
    @Value("${minio.access-key:}") private String accessKey;
    @Value("${minio.secret-key:}") private String secretKey;
    @Value("${minio.bucket:aquasuite-private}") private String bucket;

    private MinioClient minioClientOverride;

    static CreditEvidenceStorageService forTesting(MinioClient minioClient, String bucket) {
        CreditEvidenceStorageService service = new CreditEvidenceStorageService();
        service.minioClientOverride = minioClient;
        service.bucket = bucket;
        return service;
    }

    public String upload(UUID customerId, MultipartFile file) {
        validateImage(file);
        String objectKey = "credit-profile-evidence/" + customerId + "/" + UUID.randomUUID() + extensionFor(file.getContentType());
        try (InputStream input = file.getInputStream()) {
            MinioClient client = client();
            ensureBucket(client);
            client.putObject(PutObjectArgs.builder().bucket(bucket).object(objectKey).stream(input, file.getSize(), -1)
                    .contentType(file.getContentType()).build());
            return objectKey;
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_UPLOAD_FAILED);
        }
    }

    public void requireOwnedObject(UUID customerId, String objectKey) {
        String prefix = "credit-profile-evidence/" + customerId + "/";
        if (objectKey == null || !objectKey.startsWith(prefix) || objectKey.contains("..")) {
            throw new AppException(CrmErrorCode.INVALID_CREDIT_EVIDENCE);
        }
        try {
            client().statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_NOT_FOUND);
        }
    }

    public CreditEvidenceDocument read(String objectKey) {
        if (objectKey == null || objectKey.isBlank()) throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_NOT_FOUND);
        try {
            MinioClient client = client();
            var stat = client.statObject(StatObjectArgs.builder().bucket(bucket).object(objectKey).build());
            try (InputStream input = client.getObject(GetObjectArgs.builder().bucket(bucket).object(objectKey).build())) {
                String contentType = stat.contentType() == null ? "application/octet-stream" : stat.contentType();
                return new CreditEvidenceDocument(input.readAllBytes(), contentType, fileName(objectKey));
            }
        } catch (AppException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_NOT_FOUND);
        }
    }

    private MinioClient client() {
        if (minioClientOverride != null) {
            return minioClientOverride;
        }
        if (blank(endpoint) || blank(accessKey) || blank(secretKey)) throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_STORAGE_UNAVAILABLE);
        return MinioClient.builder().endpoint(endpoint.trim()).credentials(accessKey.trim(), secretKey.trim()).build();
    }

    private void ensureBucket(MinioClient client) throws Exception {
        if (!client.bucketExists(BucketExistsArgs.builder().bucket(bucket).build())) client.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw new AppException(CrmErrorCode.INVALID_CREDIT_EVIDENCE);
        if (file.getSize() > MAX_FILE_SIZE) throw new AppException(CrmErrorCode.CREDIT_EVIDENCE_TOO_LARGE);
        if (file.getContentType() == null || !ALLOWED_CONTENT_TYPES.contains(file.getContentType().toLowerCase(Locale.ROOT))) throw new AppException(CrmErrorCode.INVALID_CREDIT_EVIDENCE);
    }

    private String extensionFor(String contentType) {
        return switch (contentType.toLowerCase(Locale.ROOT)) {
            case "image/jpeg", "image/jpg" -> ".jpg";
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            default -> throw new AppException(CrmErrorCode.INVALID_CREDIT_EVIDENCE);
        };
    }

    private String fileName(String objectKey) { return objectKey.substring(objectKey.lastIndexOf('/') + 1); }
    private boolean blank(String value) { return value == null || value.isBlank(); }
}
