package com.aquasuite.ims.module.crm.internal.storage;

import com.aquasuite.ims.module.crm.internal.error.CrmErrorCode;
import com.aquasuite.ims.share.exception.AppException;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.GetObjectResponse;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreditEvidenceStorageServiceTest {

    private static final String BUCKET = "aquasuite-private";
    private static final UUID CUSTOMER_ID = UUID.fromString("30000000-0000-4000-8000-000000000001");

    @Mock
    private MinioClient minioClient;

    private CreditEvidenceStorageService storageService;

    @BeforeEach
    void setUp() {
        storageService = CreditEvidenceStorageService.forTesting(minioClient, BUCKET);
    }

    @Test
    void upload_nullOrEmptyFile_throwsInvalidCreditEvidence() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "cccd.png", "image/png", new byte[0]);

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, null))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, emptyFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_fileExceeding5MiB_throwsCreditEvidenceTooLarge() throws Exception {
        byte[] oversized = new byte[5 * 1024 * 1024 + 1];
        MockMultipartFile largeFile = new MockMultipartFile("file", "cccd.jpg", "image/jpeg", oversized);

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, largeFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_EVIDENCE_TOO_LARGE);

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_invalidMimeType_throwsInvalidCreditEvidence() throws Exception {
        MockMultipartFile pdfFile = new MockMultipartFile("file", "cccd.pdf", "application/pdf", new byte[] {1, 2, 3});
        MockMultipartFile nullTypeFile = new MockMultipartFile("file", "cccd.bin", null, new byte[] {1, 2, 3});

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, pdfFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, nullTypeFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);

        verify(minioClient, never()).putObject(any(PutObjectArgs.class));
    }

    @Test
    void upload_validImage_createsMissingBucketAndStoresPrivateObjectInMinio() throws Exception {
        byte[] imageBytes = "png-binary-content".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pngFile = new MockMultipartFile("file", "cccd.png", "image/png", imageBytes);
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(false);

        String objectKey = storageService.upload(CUSTOMER_ID, pngFile);

        assertThat(objectKey)
                .startsWith("credit-profile-evidence/" + CUSTOMER_ID + "/")
                .endsWith(".png");

        ArgumentCaptor<MakeBucketArgs> makeBucketCaptor = ArgumentCaptor.forClass(MakeBucketArgs.class);
        verify(minioClient).makeBucket(makeBucketCaptor.capture());
        assertThat(makeBucketCaptor.getValue().bucket()).isEqualTo(BUCKET);

        ArgumentCaptor<PutObjectArgs> putCaptor = ArgumentCaptor.forClass(PutObjectArgs.class);
        verify(minioClient).putObject(putCaptor.capture());
        assertThat(putCaptor.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(putCaptor.getValue().object()).isEqualTo(objectKey);
        assertThat(putCaptor.getValue().contentType()).isEqualTo("image/png");
    }

    @Test
    void upload_existingBucketWithJpegAndWebp_skipsMakeBucketAndMapsExtensions() throws Exception {
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        MockMultipartFile jpegFile = new MockMultipartFile("file", "cccd.jpg", "image/jpeg", new byte[] {10, 20});
        MockMultipartFile webpFile = new MockMultipartFile("file", "cccd.webp", "image/webp", new byte[] {30, 40});

        String jpegKey = storageService.upload(CUSTOMER_ID, jpegFile);
        String webpKey = storageService.upload(CUSTOMER_ID, webpFile);

        assertThat(jpegKey).startsWith("credit-profile-evidence/" + CUSTOMER_ID + "/").endsWith(".jpg");
        assertThat(webpKey).startsWith("credit-profile-evidence/" + CUSTOMER_ID + "/").endsWith(".webp");
        verify(minioClient, never()).makeBucket(any(MakeBucketArgs.class));
    }

    @Test
    void upload_whenMinioPutFails_throwsCreditEvidenceUploadFailed() throws Exception {
        MockMultipartFile pngFile = new MockMultipartFile("file", "cccd.png", "image/png", new byte[] {1, 2, 3});
        when(minioClient.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        when(minioClient.putObject(any(PutObjectArgs.class))).thenThrow(new RuntimeException("MinIO network error"));

        assertThatThrownBy(() -> storageService.upload(CUSTOMER_ID, pngFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_EVIDENCE_UPLOAD_FAILED);
    }

    @Test
    void upload_whenMinioCredentialsMissing_throwsStorageUnavailable() {
        CreditEvidenceStorageService unconfigured = new CreditEvidenceStorageService();
        MockMultipartFile pngFile = new MockMultipartFile("file", "cccd.png", "image/png", new byte[] {1, 2, 3});

        assertThatThrownBy(() -> unconfigured.upload(CUSTOMER_ID, pngFile))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_EVIDENCE_STORAGE_UNAVAILABLE);
    }

    @Test
    void requireOwnedObject_enforcesCustomerOwnershipPathSafetyAndMinioExistence() throws Exception {
        String validKey = "credit-profile-evidence/" + CUSTOMER_ID + "/doc-001.png";
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(mock(StatObjectResponse.class));

        storageService.requireOwnedObject(CUSTOMER_ID, validKey);
        verify(minioClient).statObject(any(StatObjectArgs.class));

        assertThatThrownBy(() -> storageService.requireOwnedObject(CUSTOMER_ID, "credit-profile-evidence/" + UUID.randomUUID() + "/doc-001.png"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);

        assertThatThrownBy(() -> storageService.requireOwnedObject(CUSTOMER_ID, "credit-profile-evidence/" + CUSTOMER_ID + "/../secret.png"))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.INVALID_CREDIT_EVIDENCE);
    }

    @Test
    void read_streamsStoredEvidenceDocumentFromMinioAndHandlesMissingObjects() throws Exception {
        String objectKey = "credit-profile-evidence/" + CUSTOMER_ID + "/evidence-cccd.webp";
        byte[] expectedBytes = "private-cccd-stream".getBytes(StandardCharsets.UTF_8);

        StatObjectResponse statResponse = mock(StatObjectResponse.class);
        when(statResponse.contentType()).thenReturn("image/webp");
        when(minioClient.statObject(any(StatObjectArgs.class))).thenReturn(statResponse);

        GetObjectResponse objectStream = mock(GetObjectResponse.class);
        when(objectStream.readAllBytes()).thenReturn(expectedBytes);
        when(minioClient.getObject(any(GetObjectArgs.class))).thenReturn(objectStream);

        CreditEvidenceDocument document = storageService.read(objectKey);

        assertThat(document.content()).isEqualTo(expectedBytes);
        assertThat(document.contentType()).isEqualTo("image/webp");
        assertThat(document.fileName()).isEqualTo("evidence-cccd.webp");

        assertThatThrownBy(() -> storageService.read(" "))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_EVIDENCE_NOT_FOUND);

        when(minioClient.statObject(any(StatObjectArgs.class))).thenThrow(new RuntimeException("NoSuchKey"));
        assertThatThrownBy(() -> storageService.read(objectKey))
                .isInstanceOf(AppException.class)
                .extracting(ex -> ((AppException) ex).getErrorCode())
                .isEqualTo(CrmErrorCode.CREDIT_EVIDENCE_NOT_FOUND);
    }
}
