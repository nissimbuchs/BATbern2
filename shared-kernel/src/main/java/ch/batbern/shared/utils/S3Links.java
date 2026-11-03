package ch.batbern.shared.utils;

import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;

/**
 * Presigned S3 links, built one way for every service (#1187). Before, eight services each built
 * their own {@code PutObjectPresignRequest} / {@code GetObjectPresignRequest}.
 *
 * <p>A presigned link is a bearer credential until it expires: whoever holds it can upload or
 * download. Never log it; log the S3 key instead.
 */
public final class S3Links {

    private S3Links() {
    }

    /**
     * A presigned PUT for a direct browser upload.
     *
     * @param contentType   signed into the link, so the upload must send the same Content-Type
     * @param contentLength signed into the link when not null, so the upload must have exactly this size
     */
    public static String uploadUrl(S3Presigner presigner, String bucket, String key, String contentType,
            Long contentLength, Duration validity) {
        Objects.requireNonNull(presigner, "presigner");
        PutObjectRequest.Builder put = PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType);
        if (contentLength != null) {
            put.contentLength(contentLength);
        }
        return presigner.presignPutObject(PutObjectPresignRequest.builder()
                .signatureDuration(validity)
                .putObjectRequest(put.build())
                .build()).url().toString();
    }

    /** A presigned GET that opens the object in the browser. */
    public static String downloadUrl(S3Presigner presigner, String bucket, String key, Duration validity) {
        return downloadUrl(presigner, bucket, key, validity, null);
    }

    /**
     * A presigned GET.
     *
     * @param attachmentName when not null, the response is a download under this file name
     *                       ({@code Content-Disposition: attachment; filename*=UTF-8''...}, RFC 6266)
     */
    public static String downloadUrl(S3Presigner presigner, String bucket, String key, Duration validity,
            String attachmentName) {
        Objects.requireNonNull(presigner, "presigner");
        GetObjectRequest.Builder get = GetObjectRequest.builder().bucket(bucket).key(key);
        if (attachmentName != null) {
            String encoded = URLEncoder.encode(attachmentName, StandardCharsets.UTF_8).replace("+", "%20");
            get.responseContentDisposition("attachment; filename*=UTF-8''" + encoded);
        }
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(validity)
                .getObjectRequest(get.build())
                .build()).url().toString();
    }
}
