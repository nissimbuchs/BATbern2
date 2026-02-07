package ch.batbern.events.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Exception thrown when a photo upload cannot be found in S3.
 * Story 6.2b: Speaker Profile Update Portal - AC7 (Profile Photo Upload)
 *
 * This occurs when:
 * - The uploadId doesn't exist
 * - The file was never uploaded to S3
 * - The presigned URL expired before upload completed
 * - The file was deleted from S3
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class PhotoUploadNotFoundException extends RuntimeException {

    private static final String ERROR_CODE = "UPLOAD_NOT_FOUND";

    public PhotoUploadNotFoundException(String uploadId) {
        super("Photo upload not found in S3 for uploadId: " + uploadId);
    }

    public PhotoUploadNotFoundException(String uploadId, Throwable cause) {
        super("Photo upload not found in S3 for uploadId: " + uploadId, cause);
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
