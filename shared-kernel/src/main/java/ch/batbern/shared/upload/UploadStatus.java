package ch.batbern.shared.upload;

/**
 * Lifecycle of an upload (ADR-002): PENDING (reserved, presigned URL handed out) → CONFIRMED (the
 * file is in S3) → ASSOCIATED (attached to its entity, at its final key).
 *
 * <p>Stored lower case ({@link UploadStatusConverter}), sent upper case.
 */
public enum UploadStatus {
    PENDING,
    CONFIRMED,
    ASSOCIATED
}
