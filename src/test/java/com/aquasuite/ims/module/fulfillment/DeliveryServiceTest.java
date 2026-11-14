package com.aquasuite.ims.module.fulfillment;

import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests;
import com.aquasuite.ims.module.fulfillment.internal.repository.*;
import com.aquasuite.ims.module.fulfillment.internal.service.DeliveryService;
import com.aquasuite.ims.module.fulfillment.internal.storage.DeliveryPhotoStorage;
import com.aquasuite.ims.module.iam.api.*;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.share.exception.AppException;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.*;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.data.domain.PageRequest;
import java.util.*;
import java.time.OffsetDateTime;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.assertj.core.api.Assertions.*;

class DeliveryServiceTest {
    DeliveryRepository repo;
    DeliveryItemRepository items;
    IamApi actor;
    IamApi iam;
    CatalogApi catalog;
    DeliveryPhotoStorage storage;
    PlatformTransactionManager manager;
    DeliveryService service;
    UUID shipper = UUID.randomUUID(), accountant = UUID.randomUUID();
    Delivery d;

    @BeforeEach void setup() {
        repo = mock(DeliveryRepository.class); items = mock(DeliveryItemRepository.class);
        actor = mock(IamApi.class); iam = actor; catalog = mock(CatalogApi.class);
        storage = mock(DeliveryPhotoStorage.class); manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenAnswer(x -> new SimpleTransactionStatus());
        service = new DeliveryService(repo, items, iam, catalog, storage, manager);
        d = Delivery.builder().deliveryId(UUID.randomUUID()).orderId(UUID.randomUUID())
            .shipperId(shipper).deliveryStatus("IN_TRANSIT").note("Keep this note")
            .confirmedAt(OffsetDateTime.now()).build();
        when(repo.lockById(d.getDeliveryId())).thenReturn(Optional.of(d));
        when(repo.findById(d.getDeliveryId())).thenReturn(Optional.of(d));
        login(shipper, "SHIPPER");
    }
    void login(UUID id, String role) {
        when(actor.getCurrentUser()).thenReturn(UserSummaryDto.builder().id(id).status("ACTIVE").roles(List.of(role)).build());
    }
    MockMultipartFile photo() throws Exception {
        var output = new ByteArrayOutputStream();
        ImageIO.write(new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output);
        return new MockMultipartFile("photos", "proof.png", "image/png", output.toByteArray());
    }
    @Test void ownerCannotStartDeliveryAsShipper() {
        login(accountant, "SHOP_OWNER");
        assertThatThrownBy(() -> service.start(d.getDeliveryId())).isInstanceOf(AppException.class);
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void rescheduleRejectsPastDateAndOtherShippers() {
        assertThatThrownBy(() -> service.reschedule(d.getDeliveryId(),new DeliveryRequests.Reschedule(
                OffsetDateTime.now().minusDays(1),"Hẹn ngày khác"))).isInstanceOf(AppException.class);
        login(UUID.randomUUID(),"SHIPPER");
        assertThatThrownBy(() -> service.reschedule(d.getDeliveryId(),new DeliveryRequests.Reschedule(
                OffsetDateTime.now().plusDays(1),"Hẹn ngày khác"))).isInstanceOf(AppException.class);
        assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT");
    }
    @Test void temporaryObstacleCanWaitWithoutDateAndCannotFailWithoutCustomerConfirmation() {
        assertThatThrownBy(() -> service.fail(d.getDeliveryId(),new DeliveryRequests.Fail("Không liên hệ được",false)))
                .isInstanceOf(AppException.class);
        assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT");
        var waiting=new DeliveryRequests.Reschedule(null,"Không liên hệ được khách");
        var result=service.reschedule(d.getDeliveryId(),waiting);
        assertThat(result.deliveryStatus()).isEqualTo("RESCHEDULED");
        assertThat(result.scheduledDate()).isNull();
        assertThat(service.reschedule(d.getDeliveryId(),waiting).note()).isEqualTo(result.note());
        var agreed=new DeliveryRequests.Reschedule(OffsetDateTime.now().plusDays(1),"Đã liên hệ và chốt lịch");
        assertThat(service.reschedule(d.getDeliveryId(),agreed).scheduledDate()).isEqualTo(
                agreed.scheduledDate().truncatedTo(java.time.temporal.ChronoUnit.MICROS));
        assertThatThrownBy(() -> service.reschedule(d.getDeliveryId(),waiting)).isInstanceOf(AppException.class);
    }
    @Test void rescheduleCannotReopenTerminalDeliveryOrCompleteWithoutResuming() throws Exception {
        for(String state:List.of("FAILED","DELIVERED","CANCELLED","DRAFT")) {
            d.setDeliveryStatus(state);
            assertThatThrownBy(() -> service.reschedule(d.getDeliveryId(),new DeliveryRequests.Reschedule(
                    OffsetDateTime.now().plusDays(1),"Hẹn ngày khác"))).isInstanceOf(AppException.class);
        }
        d.setDeliveryStatus("IN_TRANSIT");
        var request=new DeliveryRequests.Reschedule(OffsetDateTime.now().plusDays(1),"Khách hẹn lại");
        service.reschedule(d.getDeliveryId(),request);
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(),List.of(photo()))).isInstanceOf(AppException.class);
        assertThatThrownBy(() -> service.reschedule(d.getDeliveryId(),new DeliveryRequests.Reschedule(
                OffsetDateTime.now().plusDays(2),"Nội dung khác"))).isInstanceOf(AppException.class);
        assertThat(d.getDeliveryStatus()).isEqualTo("RESCHEDULED");
        verifyNoInteractions(storage);
    }
    @Test void cannotStartAnotherShippersDelivery() {
        login(UUID.randomUUID(), "SHIPPER");
        assertThatThrownBy(() -> service.start(d.getDeliveryId())).isInstanceOf(AppException.class);
        verify(repo, never()).saveAndFlush(any());
    }
    @Test void cannotReadAnotherShippersPhotos() {
        login(UUID.randomUUID(), "SHIPPER");
        assertThatThrownBy(() -> service.readPhoto(d.getDeliveryId(), UUID.randomUUID())).isInstanceOf(AppException.class);
        verifyNoInteractions(storage);
    }
    @Test void startReplayPreservesTime() {
        d.setDeliveryStatus("ASSIGNED");
        service.start(d.getDeliveryId());
        var first = d.getStartedAt(); service.start(d.getDeliveryId());
        assertThat(d.getStartedAt()).isEqualTo(first);
        assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT");
    }
    @Test void failKeepsNoteAndReplayDoesNotAppendTwice() {
        service.fail(d.getDeliveryId(), new DeliveryRequests.Fail("Khách xác nhận không nhận",true));
        String note = d.getNote();
        service.fail(d.getDeliveryId(), new DeliveryRequests.Fail("Khách xác nhận không nhận",true));
        assertThat(d.getNote()).isEqualTo(note).startsWith("Keep this note").endsWith("reason: Khách xác nhận không nhận");
        assertThatThrownBy(() -> service.fail(d.getDeliveryId(), new DeliveryRequests.Fail("Different",true))).isInstanceOf(AppException.class);
    }
    @Test void completionRequiresPhotos() {
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of())).isInstanceOf(AppException.class);
        assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT"); verifyNoInteractions(storage);
    }
    @Test void disguisedTextImageIsRejected() {
        var file = new MockMultipartFile("photos", "fake.png", "image/png", "text".getBytes());
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(file))).isInstanceOf(AppException.class);
        verifyNoInteractions(storage);
    }
    @Test void mimeMismatchRejected() throws Exception {
        var file = new MockMultipartFile("photos", "fake.jpg", "image/jpeg", photo().getBytes());
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(file))).isInstanceOf(AppException.class);
        verifyNoInteractions(storage);
    }
    @Test void missingStorageNeverMarksDelivered() throws Exception {
        when(storage.store(any(), any())).thenThrow(new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE));
        var photo = photo();
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(photo))).isInstanceOf(AppException.class);
        assertThat(d.getDeliveredAt()).isNull(); assertThat(d.getProofPhotos()).isEmpty();
        assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT");
    }
    @Test void completionStoresProofAndReplayDoesNotUploadAgain() throws Exception {
        when(storage.store(any(), any())).thenReturn(new DeliveryPhotoStorage.Stored("private/key"));
        service.complete(d.getDeliveryId(), List.of(photo()));
        assertThat(d.getDeliveryStatus()).isEqualTo("DELIVERED");
        assertThat(d.getDeliveredAt()).isNotNull(); assertThat(d.getProofPhotos()).hasSize(1);
        service.complete(d.getDeliveryId(), List.of());
        verify(storage, times(1)).store(any(), any()); verify(storage, never()).delete(any());
    }
    @Test void partialStorageFailureDeletesUploadedFiles() throws Exception {
        when(storage.store(any(), any())).thenReturn(new DeliveryPhotoStorage.Stored("first"))
            .thenThrow(new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE));
        var photo = photo();
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(photo, photo))).isInstanceOf(AppException.class);
        verify(storage).delete("first"); assertThat(d.getDeliveryStatus()).isEqualTo("IN_TRANSIT");
    }
    @Test void stateChangeDuringUploadDeletesNewPhoto() throws Exception {
        when(storage.store(any(), any())).thenAnswer(x -> { d.setDeliveryStatus("FAILED"); return new DeliveryPhotoStorage.Stored("new"); });
        var photo = photo();
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(photo))).isInstanceOf(AppException.class);
        verify(storage).delete("new"); assertThat(d.getProofPhotos()).isEmpty();
    }
    @Test void rollbackCleansUploadedPhoto() throws Exception {
        when(storage.store(any(), any())).thenReturn(new DeliveryPhotoStorage.Stored("new"));
        when(repo.saveAndFlush(any())).thenThrow(new IllegalStateException("database failure"));
        var photo = photo();
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of(photo))).isInstanceOf(IllegalStateException.class);
        verify(storage).delete("new"); verify(manager).rollback(any());
    }
    @Test void assignChecksAllStatesBeforeWriting() {
        login(accountant, "ACCOUNTANT");
        when(iam.getUserById(shipper)).thenReturn(UserSummaryDto.builder().id(shipper).status("ACTIVE").roles(List.of("SHIPPER")).build());
        var second = Delivery.builder().deliveryId(UUID.randomUUID()).deliveryStatus("DELIVERED").build();
        d.setDeliveryStatus("DISPATCHED");
        when(repo.lockById(second.getDeliveryId())).thenReturn(Optional.of(second));
        assertThatThrownBy(() -> service.assign(new DeliveryRequests.Assign(shipper, List.of(d.getDeliveryId(), second.getDeliveryId())))).isInstanceOf(AppException.class);
        verify(repo, never()).save(any()); assertThat(d.getDeliveryStatus()).isEqualTo("DISPATCHED");
    }
    @Test void assignmentReplayAndUnassignKeepSourceFields() {
        login(accountant, "ACCOUNTANT");
        when(iam.getUserById(shipper)).thenReturn(UserSummaryDto.builder().id(shipper).status("ACTIVE").roles(List.of("SHIPPER")).build());
        d.setDeliveryStatus("DISPATCHED");
        var confirmed = d.getConfirmedAt();
        service.assign(new DeliveryRequests.Assign(shipper, List.of(d.getDeliveryId())));
        var assigned = d.getAssignedAt();
        service.assign(new DeliveryRequests.Assign(shipper, List.of(d.getDeliveryId())));
        assertThat(d.getAssignedAt()).isEqualTo(assigned);
        service.unassign(d.getDeliveryId()); assertThat(d.getShipperId()).isNull();
        assertThat(d.getConfirmedAt()).isEqualTo(confirmed);
    }
    @Test void invalidOrDuplicateAssignmentsRejected() {
        login(accountant, "ACCOUNTANT");
        assertThatThrownBy(() -> service.assign(new DeliveryRequests.Assign(shipper, List.of(d.getDeliveryId(), d.getDeliveryId())))).isInstanceOf(AppException.class);
        verify(iam, never()).getUserById(any());
    }
    @Test void unsafeSortRejected() {
        login(accountant, "ACCOUNTANT");
        assertThatThrownBy(() -> service.search(null, null, null, null, null, null,
            PageRequest.of(0, 20, org.springframework.data.domain.Sort.by("proofPhotos")))).isInstanceOf(AppException.class);
    }
    @Test void accountantCannotCompleteAsShipper() {
        login(accountant, "ACCOUNTANT");
        assertThatThrownBy(() -> service.complete(d.getDeliveryId(), List.of())).isInstanceOf(AppException.class);
        verifyNoInteractions(storage);
    }
}
