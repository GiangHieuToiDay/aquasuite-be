package com.aquasuite.ims.module.fulfillment.internal.service;

import com.aquasuite.ims.module.fulfillment.internal.entity.Delivery;
import com.aquasuite.ims.module.fulfillment.internal.enums.DeliveryStatus;
import com.aquasuite.ims.module.fulfillment.internal.error.FulfillmentErrorCode;
import com.aquasuite.ims.module.fulfillment.internal.payload.request.DeliveryRequests;
import com.aquasuite.ims.module.fulfillment.internal.payload.response.DeliveryView;
import com.aquasuite.ims.module.fulfillment.internal.repository.*;
import com.aquasuite.ims.module.fulfillment.internal.storage.DeliveryPhotoStorage;
import com.aquasuite.ims.module.iam.api.IamApi;
import com.aquasuite.ims.module.iam.api.dto.UserSummaryDto;
import com.aquasuite.ims.module.catalog.api.CatalogApi;
import com.aquasuite.ims.share.exception.*;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.*;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Service
public class DeliveryService {
    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);
    private static final Set<String> SORT_FIELDS = Set.of("deliveryId", "deliveryCode", "scheduledDate", "updatedAt", "createdAt", "batchNo", "deliveryStatus");
    private final DeliveryRepository deliveries;
    private final DeliveryItemRepository items;
    private final IamApi iam;
    private final CatalogApi catalog;
    private final DeliveryPhotoStorage storage;
    private final TransactionTemplate transaction;

    public DeliveryService(DeliveryRepository deliveries, DeliveryItemRepository items, IamApi iam, CatalogApi catalog, DeliveryPhotoStorage storage, PlatformTransactionManager manager) {
        this.deliveries = deliveries;
        this.items = items;
        this.iam = iam;
        this.catalog = catalog;
        this.storage = storage;
        this.transaction = new TransactionTemplate(manager);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryView.Summary> search(String status, UUID shipperId, UUID orderId, String deliveryCode,
            LocalDate from, LocalDate to, Pageable pageable) {
        requireRole("ACCOUNTANT");
        return query(status, shipperId, orderId, deliveryCode, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryView.Summary> mine(String status, LocalDate from, LocalDate to, Pageable pageable) {
        UserSummaryDto user = requireRole("SHIPPER");
        return query(status, user.id(), null, null, from, to, pageable);
    }

    @Transactional(readOnly = true)
    public Page<DeliveryView.Summary> byOrder(UUID orderId, Pageable pageable) {
        requireRole("ACCOUNTANT");
        return query(null, null, orderId, null, null, null, pageable);
    }

    private Page<DeliveryView.Summary> query(String status, UUID shipperId, UUID orderId, String code,
            LocalDate from, LocalDate to, Pageable pageable) {
        if (status != null) {
            try { DeliveryStatus.valueOf(status); } catch (IllegalArgumentException ex) { throw invalid(); }
        }
        if (from != null && to != null && from.isAfter(to)) throw invalid();
        if (pageable == null || pageable.isUnpaged() || pageable.getPageSize() > 100
                || pageable.getSort().stream().anyMatch(s -> !SORT_FIELDS.contains(s.getProperty()))) throw invalid();
        Sort sort = pageable.getSort().isSorted() ? pageable.getSort() : Sort.by(Sort.Direction.DESC, "updatedAt");
        if (sort.getOrderFor("deliveryId") == null) sort = sort.and(Sort.by("deliveryId"));
        Specification<Delivery> spec = (root, q, cb) -> {
            List<Predicate> clauses = new ArrayList<>();
            if (status != null) clauses.add(cb.equal(root.get("deliveryStatus"), status));
            if (shipperId != null) clauses.add(cb.equal(root.get("shipperId"), shipperId));
            if (orderId != null) clauses.add(cb.equal(root.get("orderId"), orderId));
            if (code != null && !code.isBlank()) clauses.add(cb.equal(root.get("deliveryCode"), code.trim()));
            ZoneId zone = ZoneId.of("Asia/Ho_Chi_Minh");
            if (from != null) clauses.add(cb.greaterThanOrEqualTo(root.get("scheduledDate"), from.atStartOfDay(zone).toOffsetDateTime()));
            if (to != null) clauses.add(cb.lessThan(root.get("scheduledDate"), to.plusDays(1).atStartOfDay(zone).toOffsetDateTime()));
            return cb.and(clauses.toArray(Predicate[]::new));
        };
        return deliveries.findAll(spec, PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)).map(this::summary);
    }

    @Transactional(readOnly = true)
    public DeliveryView.Detail detail(UUID id) {
        Delivery d = required(id);
        authorizeRead(d);
        return view(d);
    }

    public List<DeliveryView.Summary> assign(DeliveryRequests.Assign request) {
        UserSummaryDto accountant = requireRole("ACCOUNTANT");
        if (request == null || request.shipperId() == null || request.deliveryIds() == null
                || request.deliveryIds().isEmpty() || request.deliveryIds().size() > 100
                || request.deliveryIds().stream().anyMatch(Objects::isNull)
                || new HashSet<>(request.deliveryIds()).size() != request.deliveryIds().size()) throw invalid();
        UserSummaryDto shipper;
        try { shipper = iam.getUserById(request.shipperId()); }
        catch (AppException ex) { throw new AppException(FulfillmentErrorCode.INVALID_SHIPPER); }
        if (shipper == null || !request.shipperId().equals(shipper.id()) || !"ACTIVE".equals(shipper.status())
                || shipper.roles() == null || !shipper.roles().contains("SHIPPER")) throw new AppException(FulfillmentErrorCode.INVALID_SHIPPER);
        return transaction.execute(tx -> {
            List<Delivery> locked = request.deliveryIds().stream().sorted().map(this::locked).toList();
            for (Delivery d : locked) {
                boolean rescheduled = is(d, "RESCHEDULED");
                if ((!is(d, "DISPATCHED") && !is(d, "ASSIGNED") && !rescheduled)
                        || (!rescheduled && d.getStartedAt() != null) || d.getConfirmedAt() == null) throw state();
            }
            OffsetDateTime now = OffsetDateTime.now();
            for (Delivery d : locked) {
                if ((is(d, "ASSIGNED") || is(d, "RESCHEDULED")) && request.shipperId().equals(d.getShipperId())) continue;
                d.setShipperId(request.shipperId());
                d.setAssignedBy(accountant.id());
                d.setAssignedAt(now);
                if (!is(d, "RESCHEDULED")) d.setDeliveryStatus("ASSIGNED");
                deliveries.save(d);
            }
            deliveries.flush();
            return locked.stream().map(this::summary).toList();
        });
    }

    public DeliveryView.Summary unassign(UUID id) {
        requireRole("ACCOUNTANT");
        return write(id, d -> {
            if (is(d, "DISPATCHED") && d.getShipperId() == null && d.getStartedAt() == null) return;
            if (!is(d, "ASSIGNED") || d.getStartedAt() != null) throw state();
            d.setShipperId(null); d.setAssignedAt(null); d.setAssignedBy(null); d.setDeliveryStatus("DISPATCHED");
        });
    }

    public DeliveryView.Summary start(UUID id) {
        UUID shipper = requireRole("SHIPPER").id();
        return write(id, d -> {
            own(d, shipper);
            if (is(d, "IN_TRANSIT")) return;
            if (is(d, "RESCHEDULED")) {
                if (d.getConfirmedAt() == null) throw state();
                if (d.getStartedAt() == null) d.setStartedAt(OffsetDateTime.now());
                d.setDeliveryStatus("IN_TRANSIT");
                return;
            }
            if (!is(d, "ASSIGNED") || d.getStartedAt() != null) throw state();
            d.setStartedAt(OffsetDateTime.now()); d.setDeliveryStatus("IN_TRANSIT");
        });
    }

    public DeliveryView.Summary reschedule(UUID id, DeliveryRequests.Reschedule request) {
        UUID shipper = requireRole("SHIPPER").id();
        if (request == null || request.reason() == null
                || request.reason().isBlank() || request.reason().length() > 1000) throw invalid();
        var date = request.scheduledDate() == null ? null : request.scheduledDate().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        String ending = " next: " + (date == null ? "UNSCHEDULED" : date.toInstant()) + " reason: " + request.reason().trim();
        return write(id, d -> {
            own(d, shipper);
            if (is(d, "RESCHEDULED")) {
                if (Objects.equals(d.getScheduledDate() == null ? null : d.getScheduledDate().toInstant(), date == null ? null : date.toInstant())
                        && d.getNote() != null && d.getNote().endsWith(ending)) return;
                // A temporary obstacle may have no agreed date yet; allow closing that missing date once.
                if (d.getScheduledDate() != null || date == null) throw state();
            }
            if ((!is(d, "ASSIGNED") && !is(d, "IN_TRANSIT") && !is(d, "RESCHEDULED")) || d.getConfirmedAt() == null) throw state();
            if (date != null && !date.isAfter(OffsetDateTime.now())) throw invalid();
            String entry = "[Delivery rescheduled at " + OffsetDateTime.now() + " by " + shipper + "]" + ending;
            d.setNote(d.getNote() == null || d.getNote().isBlank() ? entry : d.getNote() + "\n" + entry);
            d.setScheduledDate(date);
            d.setDeliveryStatus("RESCHEDULED");
        });
    }

    public DeliveryView.Summary fail(UUID id, DeliveryRequests.Fail request) {
        UUID shipper = requireRole("SHIPPER").id();
        if (request == null || !Boolean.TRUE.equals(request.customerCancellationConfirmed())
                || request.reason() == null || request.reason().isBlank() || request.reason().length() > 1000) throw invalid();
        String reason = request.reason().trim();
        String ending = " reason: " + reason;
        return write(id, d -> {
            own(d, shipper);
            if (is(d, "FAILED")) {
                if (d.getNote() != null && d.getNote().endsWith(ending)) return;
                throw state();
            }
            if (!is(d, "IN_TRANSIT") && !is(d, "RESCHEDULED")) throw state();
            String entry = "[Delivery failed at " + OffsetDateTime.now() + " by " + shipper + "]" + ending;
            d.setNote(d.getNote() == null || d.getNote().isBlank() ? entry : d.getNote() + "\n" + entry);
            d.setDeliveryStatus("FAILED");
        });
    }

    public DeliveryView.Summary complete(UUID id, List<MultipartFile> photos) {
        UUID shipper = requireRole("SHIPPER").id();
        DeliveryView.Summary replay = transaction.execute(tx -> {
            Delivery d = locked(id); own(d, shipper);
            if (is(d, "DELIVERED")) return summary(d);
            if (!is(d, "IN_TRANSIT")) throw state();
            return null;
        });
        if (replay != null) return replay;
        if (photos == null || photos.isEmpty() || photos.size() > 5) throw photoError();
        // Validate the complete batch before creating external objects.
        List<Image> images = photos.stream().map(this::validateImage).toList();
        List<Delivery.Photo> stored = new ArrayList<>();
        boolean committed = false;
        try {
            for (Image image : images) {
                DeliveryPhotoStorage.Stored result;
                try { result = storage.store(image.bytes(), image.mime()); }
                catch (RuntimeException ex) { throw new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE); }
                if (result == null || result.key() == null || result.key().isBlank()) throw new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE);
                stored.add(new Delivery.Photo(UUID.randomUUID(), result.key(), image.mime(), image.bytes().length));
            }
            Boolean applied = transaction.execute(tx -> {
                Delivery d = locked(id); own(d, shipper);
                if (is(d, "DELIVERED")) return false;
                if (!is(d, "IN_TRANSIT")) throw state();
                d.setProofPhotos(new ArrayList<>(stored));
                d.setDeliveredAt(OffsetDateTime.now()); d.setDeliveryStatus("DELIVERED");
                deliveries.saveAndFlush(d);
                return true;
            });
            committed = Boolean.TRUE.equals(applied);
        } finally {
            if (!committed) cleanup(stored);
        }
        return transaction.execute(tx -> { Delivery d = locked(id); own(d, shipper); return summary(d); });
    }

    @Transactional(readOnly = true)
    public DeliveryPhotoStorage.Content readPhoto(UUID id, UUID photoId) {
        Delivery d = required(id); authorizeRead(d);
        Delivery.Photo photo = (d.getProofPhotos() == null ? List.<Delivery.Photo>of() : d.getProofPhotos()).stream()
                .filter(p -> p.photoId().equals(photoId)).findFirst().orElseThrow(this::notFound);
        DeliveryPhotoStorage.Content content;
        try { content = storage.read(photo.storageKey()); }
        catch (RuntimeException ex) { throw new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE); }
        if (content == null || content.bytes() == null) throw new AppException(FulfillmentErrorCode.STORAGE_UNAVAILABLE);
        return new DeliveryPhotoStorage.Content(content.bytes(), photo.mimeType());
    }

    private DeliveryView.Summary write(UUID id, java.util.function.Consumer<Delivery> change) {
        return transaction.execute(tx -> {
            Delivery d = locked(id); change.accept(d); deliveries.saveAndFlush(d); return summary(d);
        });
    }
    private Delivery required(UUID id) { return deliveries.findById(id).orElseThrow(this::notFound); }
    private Delivery locked(UUID id) { return deliveries.lockById(id).orElseThrow(this::notFound); }
    private boolean is(Delivery d, String status) { return status.equals(d.getDeliveryStatus()); }
    private void own(Delivery d, UUID user) {
        if (!user.equals(d.getShipperId())) throw new AppException(CommonErrorCode.ACCESS_DENIED);
    }
    private UserSummaryDto requireRole(String role) {
        UserSummaryDto user = iam.getCurrentUser();
        if (user == null || user.id() == null) throw new AppException(CommonErrorCode.UNAUTHORIZED);
        if (user.roles() == null || (!user.roles().contains(role)
                && !("ACCOUNTANT".equals(role) && user.roles().contains("SHOP_OWNER"))))
            throw new AppException(CommonErrorCode.ACCESS_DENIED);
        return user;
    }
    private void authorizeRead(Delivery d) {
        UserSummaryDto user = iam.getCurrentUser();
        if (user == null || user.id() == null) throw new AppException(CommonErrorCode.UNAUTHORIZED);
        if (user.roles() != null && (user.roles().contains("ACCOUNTANT") || user.roles().contains("SHOP_OWNER"))) return;
        if (user.roles() == null || !user.roles().contains("SHIPPER")) throw new AppException(CommonErrorCode.ACCESS_DENIED);
        own(d, user.id());
    }
    private DeliveryView.Detail view(Delivery d) {
        var lines = items.findByDeliveryIdOrderByDeliveryItemId(d.getDeliveryId());
        var skus = catalog.getSkuSummaries(lines.stream().map(i -> i.getVariantId()).distinct().toList());
        var views = lines.stream().map(i -> {
            var sku = skus.get(i.getVariantId());
            return new DeliveryView.Item(i.getDeliveryItemId(), i.getOrderItemId(), i.getVariantId(),
                    sku == null ? null : sku.sku(), sku == null ? null : sku.productName(),
                    sku == null ? null : sku.variantName(), i.getQuantity(), i.getSerialNumbers());
        }).toList();
        UserSummaryDto shipper = d.getShipperId() == null ? null : iam.getUserById(d.getShipperId());
        var photos = (d.getProofPhotos() == null ? List.<Delivery.Photo>of() : d.getProofPhotos()).stream()
                .map(p -> new DeliveryView.Photo(p.photoId(), "/api/v1/fulfillment/deliveries/" + d.getDeliveryId() + "/photos/" + p.photoId())).toList();
        return new DeliveryView.Detail(summary(d), d.getAssignedBy(), shipper == null ? null : shipper.fullName(),
                shipper == null ? null : shipper.phone(), views, photos);
    }
    private DeliveryView.Summary summary(Delivery d) {
        return new DeliveryView.Summary(d.getDeliveryId(), d.getDeliveryCode(), d.getOrderId(), d.getBatchNo(),
                d.getWarehouseCode(), d.getShipperId(), d.getDeliveryStatus(), d.getRecipientName(),
                d.getRecipientPhone(), d.getDeliveryAddress(), d.getScheduledDate(), d.getAssignedAt(),
                d.getStartedAt(), d.getDeliveredAt(), d.getCodRequired(), d.getCodAmount(), d.getNote(), d.getUpdatedAt(), d.getSalesInvoiceId(),
                d.getConfirmedAt(), d.getDispatchDate());
    }
    private void cleanup(List<Delivery.Photo> photos) {
        for (var photo : photos) {
            try { storage.delete(photo.storageKey()); }
            catch (RuntimeException ex) { log.error("Delivery photo cleanup failed for photoId={}; storage reconciliation required", photo.photoId()); }
        }
    }
    private record Image(byte[] bytes, String mime) {}
    private Image validateImage(MultipartFile file) {
        if (file == null || file.isEmpty() || file.getSize() > 5L * 1024 * 1024) throw photoError();
        String mime = file.getContentType();
        if (!Set.of("image/jpeg", "image/png", "image/webp").contains(mime == null ? "" : mime)) throw photoError();
        try {
            byte[] bytes;
            try (var in = file.getInputStream()) { bytes = in.readNBytes(5 * 1024 * 1024 + 1); }
            if (bytes.length == 0 || bytes.length > 5 * 1024 * 1024) throw photoError();
            try (var input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                var readers = ImageIO.getImageReaders(input);
                if (!readers.hasNext()) throw photoError();
                var reader = readers.next();
                try {
                    reader.setInput(input);
                    String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                    String actual = switch (format) { case "jpeg", "jpg" -> "image/jpeg"; case "png" -> "image/png"; case "webp" -> "image/webp"; default -> ""; };
                    long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                    if (!mime.equals(actual) || pixels <= 0 || pixels > 25_000_000 || reader.read(0) == null) throw photoError();
                } finally { reader.dispose(); }
            }
            return new Image(bytes, mime);
        } catch (IOException | IllegalArgumentException ex) { throw photoError(); }
    }
    private AppException invalid() { return new AppException(CommonErrorCode.INVALID_REQUEST_PARAM); }
    private AppException state() { return new AppException(FulfillmentErrorCode.INVALID_STATE); }
    private AppException photoError() { return new AppException(FulfillmentErrorCode.INVALID_PHOTO); }
    private AppException notFound() { return new AppException(CommonErrorCode.RESOURCE_NOT_FOUND); }
}
