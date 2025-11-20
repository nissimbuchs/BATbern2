package ch.batbern.migration.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Service for uploading files to S3 with multipart support
 * Implements AC13: Multipart upload for files >10MB
 */
@Service
public class S3UploadService {

    private static final Logger log = LoggerFactory.getLogger(S3UploadService.class);

    private final S3Client s3Client;

    @Value("${migration.s3.bucket-name}")
    private String bucketName;

    @Value("${migration.cdn.base-url:https://cdn.batbern.ch}")
    private String cdnBaseUrl;

    private static final long MULTIPART_THRESHOLD = 10 * 1024 * 1024; // 10MB
    private static final long PART_SIZE = 5 * 1024 * 1024; // 5MB per part

    public S3UploadService(S3Client s3Client) {
        this.s3Client = s3Client;
    }

    /**
     * Upload file to S3 with automatic multipart for large files
     * @param file File to upload
     * @param s3Key S3 key path
     * @param contentType MIME content type
     * @return CloudFront CDN URL
     */
    public String uploadFile(File file, String s3Key, String contentType) {
        try {
            log.info("Uploading file {} to S3 key: {}", file.getName(), s3Key);

            if (file.length() > MULTIPART_THRESHOLD) {
                log.debug("File size {} exceeds threshold, using multipart upload", file.length());
                uploadMultipart(file, s3Key, contentType);
            } else {
                uploadSinglePart(file, s3Key, contentType);
            }

            String cdnUrl = generateCdnUrl(s3Key);
            log.info("Upload complete. CDN URL: {}", cdnUrl);

            return cdnUrl;

        } catch (Exception e) {
            log.error("Failed to upload file {} to S3", file.getName(), e);
            throw new RuntimeException("S3 upload failed for file: " + file.getName(), e);
        }
    }

    /**
     * Upload file in a single PUT request (for files <10MB)
     */
    private void uploadSinglePart(File file, String s3Key, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(contentType)
            .build();

        s3Client.putObject(request, RequestBody.fromFile(file));
        log.debug("Single-part upload complete for {}", s3Key);
    }

    /**
     * Upload file using multipart upload (for files >10MB)
     * AC13: Support multipart upload for large files
     */
    private void uploadMultipart(File file, String s3Key, String contentType) throws IOException {
        // Step 1: Initiate multipart upload
        CreateMultipartUploadRequest createRequest = CreateMultipartUploadRequest.builder()
            .bucket(bucketName)
            .key(s3Key)
            .contentType(contentType)
            .build();

        CreateMultipartUploadResponse createResponse = s3Client.createMultipartUpload(createRequest);
        String uploadId = createResponse.uploadId();

        log.debug("Initiated multipart upload with ID: {}", uploadId);

        List<CompletedPart> completedParts = new ArrayList<>();

        try (FileInputStream fis = new FileInputStream(file)) {
            long fileSize = file.length();
            int partNumber = 1;

            for (long offset = 0; offset < fileSize; offset += PART_SIZE) {
                long currentPartSize = Math.min(PART_SIZE, fileSize - offset);

                // Read part data
                byte[] buffer = new byte[(int) currentPartSize];
                int bytesRead = fis.read(buffer);

                if (bytesRead != currentPartSize) {
                    throw new IOException("Failed to read expected bytes from file");
                }

                // Upload part
                UploadPartRequest partRequest = UploadPartRequest.builder()
                    .bucket(bucketName)
                    .key(s3Key)
                    .uploadId(uploadId)
                    .partNumber(partNumber)
                    .build();

                UploadPartResponse partResponse = s3Client.uploadPart(
                    partRequest,
                    RequestBody.fromBytes(buffer)
                );

                completedParts.add(CompletedPart.builder()
                    .partNumber(partNumber)
                    .eTag(partResponse.eTag())
                    .build());

                log.debug("Uploaded part {} of {} ({} bytes)", partNumber,
                    (fileSize / PART_SIZE) + 1, currentPartSize);

                partNumber++;
            }

            // Step 3: Complete multipart upload
            CompleteMultipartUploadRequest completeRequest = CompleteMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .uploadId(uploadId)
                .multipartUpload(CompletedMultipartUpload.builder()
                    .parts(completedParts)
                    .build())
                .build();

            s3Client.completeMultipartUpload(completeRequest);
            log.debug("Multipart upload complete for {} ({} parts)", s3Key, partNumber - 1);

        } catch (Exception e) {
            // Abort multipart upload on failure
            log.error("Multipart upload failed, aborting", e);
            abortMultipartUpload(s3Key, uploadId);
            throw new RuntimeException("Multipart upload failed", e);
        }
    }

    /**
     * Abort multipart upload on failure
     */
    private void abortMultipartUpload(String s3Key, String uploadId) {
        try {
            AbortMultipartUploadRequest abortRequest = AbortMultipartUploadRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .uploadId(uploadId)
                .build();

            s3Client.abortMultipartUpload(abortRequest);
            log.info("Aborted multipart upload for {}", s3Key);

        } catch (Exception e) {
            log.error("Failed to abort multipart upload", e);
        }
    }

    /**
     * Generate CloudFront CDN URL from S3 key
     * AC16: Generate CDN URLs in format https://cdn.batbern.ch/{s3-key}
     */
    public String generateCdnUrl(String s3Key) {
        return cdnBaseUrl + "/" + s3Key;
    }

    /**
     * Check if file exists in S3 (for idempotency)
     */
    public boolean fileExists(String s3Key) {
        try {
            HeadObjectRequest request = HeadObjectRequest.builder()
                .bucket(bucketName)
                .key(s3Key)
                .build();

            s3Client.headObject(request);
            return true;

        } catch (NoSuchKeyException e) {
            return false;
        }
    }
}
