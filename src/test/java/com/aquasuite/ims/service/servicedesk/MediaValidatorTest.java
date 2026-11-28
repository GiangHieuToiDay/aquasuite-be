package com.aquasuite.ims.service.servicedesk;

import com.aquasuite.ims.entity.servicedesk.TicketMedia;
import com.aquasuite.ims.exception.servicedesk.ServicedeskErrorCode;
import com.aquasuite.ims.service.servicedesk.helper.MediaValidator;
import com.aquasuite.ims.exception.AppException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MediaValidatorTest {

    private final MediaValidator validator = new MediaValidator();
    private final UUID customerId = UUID.randomUUID();

    @Test
    @DisplayName("validateImage: Ảnh JPEG hợp lệ <= 5MB -> OK")
    void validateImage_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", new byte[1024 * 1024]
        );
        assertThatCode(() -> validator.validateImage(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateImage: Ảnh > 5MB -> ném INVALID_MEDIA_FILE")
    void validateImage_tooLarge() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.png", "image/png", new byte[6 * 1024 * 1024]
        );
        assertThatThrownBy(() -> validator.validateImage(file))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("validateImage: Sai định dạng (gif, pdf) -> ném INVALID_MEDIA_FILE")
    void validateImage_invalidType() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", new byte[1024]
        );
        assertThatThrownBy(() -> validator.validateImage(file))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("validateVideo: Video MP4 hợp lệ <= 20MB -> OK")
    void validateVideo_success() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[5 * 1024 * 1024]
        );
        assertThatCode(() -> validator.validateVideo(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("validateVideo: Video > 20MB -> ném INVALID_MEDIA_FILE")
    void validateVideo_tooLarge() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "demo.mp4", "video/mp4", new byte[25 * 1024 * 1024]
        );
        assertThatThrownBy(() -> validator.validateVideo(file))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("validateMediaList: Quá 5 ảnh -> ném MEDIA_COUNT_LIMIT_EXCEEDED (SVD_025)")
    void validateMediaList_tooManyImages() {
        List<TicketMedia> list = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            list.add(TicketMedia.builder()
                    .type("IMAGE")
                    .url("https://res.cloudinary.com/demo/aquasuite/warranty-tickets/" + customerId + "/img" + i + ".jpg")
                    .publicId("aquasuite/warranty-tickets/" + customerId + "/img" + i)
                    .build());
        }

        assertThatThrownBy(() -> validator.validateMediaList(list, customerId))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.MEDIA_COUNT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("validateMediaList: Quá 1 video -> ném MEDIA_COUNT_LIMIT_EXCEEDED (SVD_025)")
    void validateMediaList_tooManyVideos() {
        List<TicketMedia> list = List.of(
                TicketMedia.builder().type("VIDEO").url("https://res.cloudinary.com/demo/aquasuite/warranty-tickets/" + customerId + "/v1.mp4").publicId("v1").build(),
                TicketMedia.builder().type("VIDEO").url("https://res.cloudinary.com/demo/aquasuite/warranty-tickets/" + customerId + "/v2.mp4").publicId("v2").build()
        );

        assertThatThrownBy(() -> validator.validateMediaList(list, customerId))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.MEDIA_COUNT_LIMIT_EXCEEDED);
    }

    @Test
    @DisplayName("validateImage: File rỗng hoặc sai đuôi mở rộng -> ném INVALID_MEDIA_FILE")
    void validateImage_emptyOrBadExtension() {
        MockMultipartFile emptyFile = new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[0]);
        assertThatThrownBy(() -> validator.validateImage(emptyFile))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile spoofedFile = new MockMultipartFile("file", "payload.exe", "image/jpeg", new byte[128]);
        assertThatThrownBy(() -> validator.validateImage(spoofedFile))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("validateVideo: File rỗng hoặc sai đuôi mở rộng -> ném INVALID_MEDIA_FILE")
    void validateVideo_emptyOrBadExtension() {
        MockMultipartFile emptyVideo = new MockMultipartFile("file", "clip.mp4", "video/mp4", new byte[0]);
        assertThatThrownBy(() -> validator.validateVideo(emptyVideo))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        MockMultipartFile badExtVideo = new MockMultipartFile("file", "clip.avi", "video/mp4", new byte[512]);
        assertThatThrownBy(() -> validator.validateVideo(badExtVideo))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);
    }

    @Test
    @DisplayName("validateMediaList: Thiếu URL/publicId hoặc URL ngoài thư mục khách hàng -> ném INVALID_MEDIA_FILE")
    void validateMediaList_missingUrlOrWrongOwnerFolder() {
        List<TicketMedia> missingUrl = List.of(TicketMedia.builder()
                .type("IMAGE").url(" ").publicId("abc").build());
        assertThatThrownBy(() -> validator.validateMediaList(missingUrl, customerId))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        UUID otherCustomer = UUID.randomUUID();
        List<TicketMedia> wrongFolder = List.of(TicketMedia.builder()
                .type("IMAGE")
                .url("https://res.cloudinary.com/demo/aquasuite/warranty-tickets/" + otherCustomer + "/img.jpg")
                .publicId("aquasuite/warranty-tickets/" + otherCustomer + "/img")
                .build());
        assertThatThrownBy(() -> validator.validateMediaList(wrongFolder, customerId))
                .isInstanceOf(AppException.class)
                .matches(ex -> ((AppException) ex).getErrorCode() == ServicedeskErrorCode.INVALID_MEDIA_FILE);

        assertThatCode(() -> validator.validateMediaList(null, customerId)).doesNotThrowAnyException();
    }
}

