package ch.batbern.shared.utils;

/**
 * Utility for building CDN URLs from S3 keys.
 *
 * Handles the difference between local development (MinIO) and production (CloudFront):
 * - MinIO uses path-style access: http://localhost:8450/{bucketName}/{s3Key}
 * - CloudFront uses origin-based access: https://cdn.batbern.ch/{s3Key}
 *
 * Without this distinction, MinIO interprets the first path segment of the S3 key
 * as the bucket name, causing NoSuchKey errors when the key starts with the bucket name.
 */
public class CloudFrontUrlBuilder {

    private CloudFrontUrlBuilder() {
        // Utility class, prevent instantiation
    }

    /**
     * Build a CDN URL for the given S3 key.
     *
     * @param cloudFrontDomain the CDN domain (e.g. "https://cdn.batbern.ch" or "http://localhost:8450")
     * @param bucketName       the S3 bucket name
     * @param s3Key            the S3 object key
     * @return the full CDN URL
     */
    public static String buildUrl(String cloudFrontDomain, String bucketName, String s3Key) {
        String domain = cloudFrontDomain != null ? cloudFrontDomain : "";

        // For MinIO (local dev), include bucket name in URL path
        if (domain.contains("localhost")) {
            return domain + "/" + bucketName + "/" + s3Key;
        }

        // For CloudFront (staging/prod), bucket is configured in the CloudFront origin
        return domain + "/" + s3Key;
    }
}
