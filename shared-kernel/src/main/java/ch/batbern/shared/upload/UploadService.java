package ch.batbern.shared.upload;

import ch.batbern.shared.utils.CloudFrontUrlBuilder;
import ch.batbern.shared.utils.S3Links;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CopyObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * The upload mechanics every service shares (ADR-002 decision 2026-11-07, Story 17.11): key layout,
 * presign, type and size rules, PENDING → CONFIRMED → ASSOCIATED, expiry. What differs per purpose is
 * in its {@link UploadPurposeHandler}; the table is the service's own ({@link UploadRecordRepository}).
 *
 * <p>Not a component: each service builds one in its configuration with its repository and handlers,
 * so a service without uploads gets none from the shared component scan.
 *
 * @param <T> the service's upload entity
 */
@Slf4j
public class UploadService<T extends UploadRecord> {

    /** How long a presigned upload URL works. */
    public static final Duration PRESIGN_VALIDITY = Duration.ofMinutes(15);
    /** A reserved upload that is never confirmed is deleted after this. */
    public static final Duration PENDING_LIFETIME = Duration.ofHours(24);
    /** A confirmed upload that no entity takes is deleted after this. */
    public static final Duration CONFIRMED_LIFETIME = Duration.ofDays(7);

    private final UploadRecordRepository<T> repository;
    private final Supplier<T> newRecord;
    private final Map<String, UploadPurposeHandler> handlers;
    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final String bucket;
    private final String cdnDomain;
    private final Clock clock;

    public UploadService(UploadRecordRepository<T> repository, Supplier<T> newRecord,
            Collection<? extends UploadPurposeHandler> handlers, S3Client s3Client, S3Presigner s3Presigner,
            UploadStorage storage, Clock clock) {
        this.repository = repository;
        this.newRecord = newRecord;
        this.handlers = handlers.stream()
                .collect(Collectors.toUnmodifiableMap(UploadPurposeHandler::purpose, Function.identity()));
        this.s3Client = s3Client;
        this.s3Presigner = s3Presigner;
        this.bucket = storage.bucket();
        this.cdnDomain = storage.cdnDomain();
        this.clock = clock;
    }

    /** Where files go: the bucket and the CDN in front of it. */
    public record UploadStorage(String bucket, String cdnDomain) {
    }

    /** A reserved upload: where to PUT it, until when, with which headers. */
    public record PresignedUpload(String uploadId, String uploadUrl, Instant expiresAt,
            Map<String, String> requiredHeaders) {
    }

    /**
     * Reserves an upload (PENDING) and presigns its PUT. With a target, checks it first and binds the
     * upload to it: the confirm must name the same target (Story 17.11b).
     *
     * @param target the target keys the client sent (empty: none)
     */
    @Transactional
    public PresignedUpload create(String purpose, String fileName, String contentType, long size,
            Map<String, String> target) {
        UploadPurposeHandler handler = handler(purpose);
        handler.checkMayUpload();
        String targetId = targetId(handler, target);
        if (targetId != null) {
            handler.checkTarget(targetId);
        }
        String type = normalizedType(contentType);
        String extension = checkedExtension(handler, fileName);
        checkType(handler, type);
        checkSize(handler, size);

        String uploadId = UUID.randomUUID().toString();
        String tempKey = handler.tempKey(uploadId, extension);
        Instant now = clock.instant();
        T upload = newRecord.get();
        UploadRecord.Binding binding = targetId == null ? UploadRecord.Binding.NONE
                : new UploadRecord.Binding(handler.targetType(), targetId);
        upload.start(uploadId, purpose, new UploadRecord.TempFile(tempKey, extension, size, type), binding,
                UploadPurposeHandler.callerName(), now, now.plus(PENDING_LIFETIME));
        repository.save(upload);

        String url = S3Links.uploadUrl(s3Presigner, bucket, tempKey, type, size, PRESIGN_VALIDITY);
        log.info("UPLOAD_RESERVED purpose={} uploadId={} key={}", purpose, uploadId, tempKey);
        return new PresignedUpload(uploadId, url, now.plus(PRESIGN_VALIDITY), Map.of("Content-Type", type));
    }

    /**
     * Confirms that the file is in S3 (PENDING → CONFIRMED). With a target, attaches it right away
     * (ASSOCIATED) and the handler puts it on the target. A target other than the one given on create
     * is 404, as if the upload did not exist for it (Story 17.11b).
     */
    @Transactional
    public T confirm(String purpose, String uploadId, Map<String, String> target) {
        UploadPurposeHandler handler = handler(purpose);
        handler.checkMayUpload();
        String targetId = targetId(handler, target);
        if (targetId != null) {
            handler.checkTarget(targetId);
        }
        T upload = live(purpose, uploadId);
        if (targetId != null) {
            // Before the 409: another target never learns that the upload exists (Story 17.11b).
            requireBoundTo(upload, targetId);
        }
        if (upload.getStatus() == UploadStatus.ASSOCIATED) {
            throw UploadException.conflict("Upload " + uploadId + " is already attached");
        }
        if (!objectExists(upload.getS3Key())) {
            throw UploadException.conflict("The file of upload " + uploadId + " is not in storage yet");
        }
        Instant now = clock.instant();
        upload.confirm(now, now.plus(CONFIRMED_LIFETIME));
        repository.save(upload);
        if (targetId == null) {
            log.info("UPLOAD_CONFIRMED purpose={} uploadId={}", purpose, uploadId);
            return upload;
        }
        T associated = associate(handler, upload, handler.targetType(), targetId,
                handler.finalKey(upload, targetId));
        handler.attach(associated, targetId);
        return associated;
    }

    /**
     * Server-side import of an image from an HTTPS URL (ADR-002 Scope Clarification 2): stored at a
     * temp key as a CONFIRMED upload, ready for the entity create.
     */
    @Transactional
    public T importFromUrl(String purpose, String url, String baseName) {
        UploadPurposeHandler handler = handler(purpose);
        if (!handler.supportsImport()) {
            throw UploadException.notFound("No import for " + purpose);
        }
        handler.checkMayImport();
        URI uri = httpsUri(url);
        ImageUrlFetcher.FetchedImage image;
        try {
            image = ImageUrlFetcher.fetch(uri.toString(), handler.rules().maxBytes());
        } catch (ImageUrlFetcher.ImageFetchException e) {
            throw switch (e.getReason()) {
                case NOT_AN_IMAGE, TOO_LARGE -> UploadException.badRequest(e.getMessage());
                case HTTP_STATUS, IO -> UploadException.badGateway(e.getMessage());
            };
        }
        String type = normalizedType(image.contentType());
        checkType(handler, type);
        String extension = image.extension().toLowerCase(Locale.ROOT);
        if (!handler.rules().extensions().contains(extension)) {
            throw UploadException.badRequest("Type " + type + " is not allowed for " + purpose);
        }

        String uploadId = UUID.randomUUID().toString();
        String tempKey = handler.tempKey(uploadId, extension);
        s3Client.putObject(PutObjectRequest.builder().bucket(bucket).key(tempKey).contentType(type)
                .contentLength((long) image.body().length).build(), RequestBody.fromBytes(image.body()));

        Instant now = clock.instant();
        T upload = newRecord.get();
        upload.start(uploadId, purpose, new UploadRecord.TempFile(tempKey, extension, image.body().length, type),
                UploadRecord.Binding.NONE, UploadPurposeHandler.callerName(), now, now.plus(PENDING_LIFETIME));
        upload.confirm(now, now.plus(CONFIRMED_LIFETIME));
        repository.save(upload);
        log.info("UPLOAD_IMPORTED purpose={} uploadId={} bytes={} name={}", purpose, uploadId,
                image.body().length, baseName);
        return upload;
    }

    /**
     * Attaches a CONFIRMED upload to an entity at the purpose's final key (the entity create or
     * update that takes an {@code …UploadId}). The caller has authorized the change of the entity.
     *
     * @throws UploadException 404 unknown, expired or reserved for another target, 409 not CONFIRMED
     */
    @Transactional
    public T associate(String purpose, String uploadId, String targetId) {
        UploadPurposeHandler handler = handler(purpose);
        T upload = confirmed(purpose, uploadId);
        requireBoundTo(upload, targetId);
        return associate(handler, upload, handler.targetType(), targetId, handler.finalKey(upload, targetId));
    }

    /** As {@link #associate(String, String, String)}, to a given target type and key. */
    @Transactional
    public T associateAt(String purpose, String uploadId, String targetType, String targetId, String finalKey) {
        UploadPurposeHandler handler = handler(purpose);
        return associate(handler, confirmed(purpose, uploadId), targetType, targetId, finalKey);
    }

    /**
     * Copies an ASSOCIATED upload to a new key and assigns it to another target; the old object stays
     * (the Mail Center keeps showing a decided logo request).
     */
    @Transactional
    public T reassign(String purpose, String uploadId, String targetType, String targetId, String newKey) {
        handler(purpose);
        T upload = repository.findByUploadId(uploadId)
                .filter(u -> u.getPurpose().equals(purpose))
                .orElseThrow(() -> UploadException.notFound("Upload " + uploadId + " not found"));
        if (upload.getStatus() != UploadStatus.ASSOCIATED) {
            throw UploadException.conflict("Upload " + uploadId + " is not attached");
        }
        copy(upload.getS3Key(), newKey);
        upload.reassign(targetType, targetId, newKey, CloudFrontUrlBuilder.buildUrl(cdnDomain, bucket, newKey),
                clock.instant());
        log.info("UPLOAD_REASSIGNED purpose={} uploadId={} target={}:{}", purpose, uploadId, targetType, targetId);
        return repository.save(upload);
    }

    /**
     * Checks, before an entity is saved, that the upload it names can be attached: 404 unknown, of
     * another purpose or expired, 409 not CONFIRMED.
     */
    @Transactional(readOnly = true)
    public T requireConfirmed(String purpose, String uploadId) {
        handler(purpose);
        return confirmed(purpose, uploadId);
    }

    /** The upload of this purpose, if it exists (any state). */
    @Transactional(readOnly = true)
    public Optional<T> find(String purpose, String uploadId) {
        return repository.findByUploadId(uploadId).filter(u -> u.getPurpose().equals(purpose));
    }

    /** Whether this service owns the purpose. */
    public boolean handles(String purpose) {
        return handlers.containsKey(purpose);
    }

    /** What one expiry run did. A failed S3 delete keeps the row, so the next run tries again. */
    public record ExpiryResult(int deleted, int failed) {
    }

    /**
     * Deletes PENDING and CONFIRMED uploads past their expiry with their S3 object. Called by the
     * service's nightly job.
     */
    @Transactional
    public ExpiryResult purgeExpired() {
        List<T> expired = repository.findByStatusInAndExpiresAtBefore(
                List.of(UploadStatus.PENDING, UploadStatus.CONFIRMED), clock.instant());
        int deleted = 0;
        int failed = 0;
        for (T upload : expired) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(upload.getS3Key()).build());
                repository.delete(upload);
                deleted++;
            } catch (RuntimeException e) {
                failed++;
                log.error("UPLOAD_EXPIRY_FAILED uploadId={} key={} cause={}", upload.getUploadId(),
                        upload.getS3Key(), e.getClass().getSimpleName());
            }
        }
        if (failed > 0) {
            Metrics.counter("batbern.uploads.expiry.failures").increment(failed);
        }
        log.info("UPLOAD_EXPIRY deleted={} failed={}", deleted, failed);
        return new ExpiryResult(deleted, failed);
    }

    private T associate(UploadPurposeHandler handler, T upload, String targetType, String targetId,
            String finalKey) {
        String tempKey = upload.getS3Key();
        copy(tempKey, finalKey);
        deleteAfterCommit(tempKey);
        String url = CloudFrontUrlBuilder.buildUrl(cdnDomain, bucket, finalKey);
        upload.associate(targetType, targetId, finalKey, url, clock.instant());
        log.info("UPLOAD_ASSOCIATED purpose={} uploadId={} target={}:{} key={}", handler.purpose(),
                upload.getUploadId(), targetType, targetId, finalKey);
        return repository.save(upload);
    }

    private UploadPurposeHandler handler(String purpose) {
        UploadPurposeHandler handler = handlers.get(purpose);
        if (handler == null) {
            throw UploadException.notFound("Unknown upload purpose: " + purpose);
        }
        return handler;
    }

    /** The upload of this purpose, not expired. */
    private T live(String purpose, String uploadId) {
        T upload = repository.findByUploadId(uploadId)
                .filter(u -> u.getPurpose().equals(purpose))
                .orElseThrow(() -> UploadException.notFound("Upload " + uploadId + " not found"));
        if (upload.isExpired(clock.instant())) {
            throw UploadException.notFound("Upload " + uploadId + " has expired");
        }
        return upload;
    }

    /** 404 when the upload was reserved for another target (Story 17.11b). */
    private static void requireBoundTo(UploadRecord upload, String targetId) {
        if (upload.isBoundElsewhere(targetId)) {
            throw UploadException.notFound("Upload " + upload.getUploadId() + " not found for " + targetId);
        }
    }

    private T confirmed(String purpose, String uploadId) {
        T upload = live(purpose, uploadId);
        if (upload.getStatus() != UploadStatus.CONFIRMED) {
            throw UploadException.conflict("Upload " + uploadId + " is " + upload.getStatus() + ", not CONFIRMED");
        }
        return upload;
    }

    private static String targetId(UploadPurposeHandler handler, Map<String, String> target) {
        Map<String, String> present = target == null ? Map.of() : target.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isBlank())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        if (present.isEmpty()) {
            if (handler.requiresTarget()) {
                throw UploadException.badRequest("The upload of " + handler.purpose() + " needs a target: "
                        + handler.targetKey());
            }
            return null;
        }
        if (present.size() > 1 || !present.containsKey(handler.targetKey())) {
            throw UploadException.badRequest("The target of " + handler.purpose() + " is " + handler.targetKey());
        }
        return present.get(handler.targetKey());
    }

    private static String normalizedType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            throw UploadException.badRequest("The content type is missing");
        }
        int semicolon = contentType.indexOf(';');
        String bare = semicolon < 0 ? contentType : contentType.substring(0, semicolon);
        return bare.trim().toLowerCase(Locale.ROOT);
    }

    private static String checkedExtension(UploadPurposeHandler handler, String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') < 0 || fileName.endsWith(".")) {
            throw UploadException.badRequest("The file name has no extension");
        }
        String extension = fileName.substring(fileName.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!handler.rules().extensions().contains(extension)) {
            throw UploadException.badRequest("Files of type ." + extension + " are not allowed for "
                    + handler.purpose() + ", allowed: " + handler.rules().extensions());
        }
        return extension;
    }

    private static void checkType(UploadPurposeHandler handler, String type) {
        if (!handler.rules().contentTypes().contains(type)) {
            throw UploadException.badRequest("Type " + type + " is not allowed for " + handler.purpose()
                    + ", allowed: " + handler.rules().contentTypes());
        }
    }

    private static void checkSize(UploadPurposeHandler handler, long size) {
        if (size <= 0) {
            throw UploadException.badRequest("The file is empty");
        }
        if (size > handler.rules().maxBytes()) {
            throw UploadException.badRequest("The file exceeds " + handler.rules().maxBytes() / (1024 * 1024)
                    + " MB for " + handler.purpose());
        }
    }

    private static URI httpsUri(String url) {
        URI uri;
        try {
            uri = URI.create(url == null ? "" : url.trim());
        } catch (IllegalArgumentException e) {
            throw UploadException.badRequest("Not a valid URL");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null) {
            throw UploadException.badRequest("Only HTTPS URLs can be imported");
        }
        return uri;
    }

    private boolean objectExists(String key) {
        try {
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            return true;
        } catch (NoSuchKeyException e) {
            return false;
        } catch (S3Exception e) {
            if (e.statusCode() == 404) {
                return false;
            }
            throw e;
        }
    }

    private void copy(String sourceKey, String destinationKey) {
        s3Client.copyObject(CopyObjectRequest.builder().sourceBucket(bucket).sourceKey(sourceKey)
                .destinationBucket(bucket).destinationKey(destinationKey).build());
    }

    /**
     * Removes the temp object once the association is committed. On a rollback the row is CONFIRMED
     * again at its temp key, so the object must still be there for a retry with the same uploadId.
     */
    private void deleteAfterCommit(String key) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    delete(key);
                }
            });
        } else {
            delete(key);
        }
    }

    /** Removes the temp object after the copy; a failure leaves only an orphan, so it is logged, not thrown. */
    private void delete(String key) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
        } catch (RuntimeException e) {
            log.warn("UPLOAD_TEMP_DELETE_FAILED key={} cause={}", key, e.getClass().getSimpleName());
            Metrics.counter("batbern.uploads.temp-delete.failures").increment();
        }
    }
}
