package ch.batbern.shared.upload;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

/**
 * One upload of one purpose (ADR-002, Story 17.11). Each service maps it to its own table
 * ({@code company_user_uploads}, {@code event_management_uploads}): all services share one
 * database, so the table name carries the service.
 *
 * <p>The state changes only through the methods below; {@link UploadService} calls them.
 */
@MappedSuperclass
@Getter
public abstract class UploadRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /** The public handle, used in the API. */
    @Column(name = "upload_id", nullable = false, unique = true, length = 100, updatable = false)
    private String uploadId;

    @Column(name = "purpose", nullable = false, length = 50, updatable = false)
    private String purpose;

    /** Temp key while PENDING or CONFIRMED, the final key once ASSOCIATED. */
    @Column(name = "s3_key", nullable = false, length = 500)
    private String s3Key;

    /** CDN URL of the final key; null until ASSOCIATED. */
    @Column(name = "file_url", length = 1000)
    private String fileUrl;

    @Column(name = "file_extension", nullable = false, length = 10, updatable = false)
    private String fileExtension;

    @Column(name = "file_size", nullable = false, updatable = false)
    private Long fileSize;

    @Column(name = "content_type", nullable = false, length = 100, updatable = false)
    private String contentType;

    @Convert(converter = UploadStatusConverter.class)
    @Column(name = "status", nullable = false, length = 20)
    private UploadStatus status;

    /**
     * What the file is for, e.g. {@code COMPANY}: the target given on create (Story 17.11b, a confirm
     * for another target is refused), else null until ASSOCIATED.
     */
    @Column(name = "target_type", length = 50)
    private String targetType;

    /** The meaningful ID of the target (company name, event code); as {@link #targetType}. */
    @Column(name = "target_id", length = 255)
    private String targetId;

    @Column(name = "created_by", length = 100, updatable = false)
    private String createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** When the expiry job deletes it: PENDING 24 h, CONFIRMED 7 d after confirm, never once ASSOCIATED. */
    @Column(name = "expires_at")
    private Instant expiresAt;

    /** The file of a new upload: its temp key and what the client declared. */
    record TempFile(String key, String extension, long size, String contentType) {
    }

    /** The target a reserved upload is bound to (Story 17.11b); both null for none. */
    record Binding(String type, String id) {
        static final Binding NONE = new Binding(null, null);
    }

    /** Fills a new record as PENDING, bound to the target given on create (if any). */
    void start(String newUploadId, String newPurpose, TempFile file, Binding binding, String by, Instant now,
            Instant expiry) {
        this.uploadId = newUploadId;
        this.purpose = newPurpose;
        this.s3Key = file.key();
        this.fileExtension = file.extension();
        this.fileSize = file.size();
        this.contentType = file.contentType();
        this.targetType = binding.type();
        this.targetId = binding.id();
        this.createdBy = by;
        this.status = UploadStatus.PENDING;
        this.createdAt = now;
        this.updatedAt = now;
        this.expiresAt = expiry;
    }

    /** PENDING → CONFIRMED; a CONFIRMED upload stays CONFIRMED with a new expiry. */
    void confirm(Instant now, Instant expiry) {
        if (status == UploadStatus.ASSOCIATED) {
            throw new IllegalStateException("Upload " + uploadId + " is already associated");
        }
        this.status = UploadStatus.CONFIRMED;
        this.expiresAt = expiry;
        this.updatedAt = now;
    }

    /** CONFIRMED → ASSOCIATED at the final key; never expires afterwards. */
    void associate(String type, String target, String finalKey, String url, Instant now) {
        if (status != UploadStatus.CONFIRMED) {
            throw new IllegalStateException("Upload " + uploadId + " must be CONFIRMED, is " + status);
        }
        this.status = UploadStatus.ASSOCIATED;
        this.targetType = type;
        this.targetId = target;
        this.s3Key = finalKey;
        this.fileUrl = url;
        this.expiresAt = null;
        this.updatedAt = now;
    }

    /** An ASSOCIATED upload moves to another target and key (a logo request accepted for its company). */
    void reassign(String type, String target, String newKey, String url, Instant now) {
        if (status != UploadStatus.ASSOCIATED) {
            throw new IllegalStateException("Upload " + uploadId + " must be ASSOCIATED, is " + status);
        }
        this.targetType = type;
        this.targetId = target;
        this.s3Key = newKey;
        this.fileUrl = url;
        this.updatedAt = now;
    }

    /**
     * True when the upload was reserved for, or is attached to, another target than this one (Story
     * 17.11b); an upload reserved without a target may go to any.
     */
    public boolean isBoundElsewhere(String target) {
        return targetId != null && !targetId.equals(target);
    }

    /** PENDING or CONFIRMED past its expiry (strictly after, as the expiry job's query). */
    public boolean isExpired(Instant now) {
        return status != UploadStatus.ASSOCIATED && expiresAt != null && now.isAfter(expiresAt);
    }
}
