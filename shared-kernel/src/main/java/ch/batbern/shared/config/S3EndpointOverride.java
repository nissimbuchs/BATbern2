package ch.batbern.shared.config;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.s3.S3BaseClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Optional S3 endpoint for the pre-deploy gate (P5 spike), where MinIO stands in for S3. Bound from
 * {@code aws.s3.endpoint}, {@code aws.s3.endpoint-access-key} and {@code aws.s3.endpoint-secret-key}
 * (env {@code AWS_S3_ENDPOINT}, ...), which the CDK stacks never set. Blank endpoint = inactive: the
 * builders stay exactly as the caller made them, so production keeps AWS S3 and its credentials chain.
 *
 * @param endpoint  the S3-compatible endpoint, blank in production
 * @param accessKey the access key of that endpoint (MinIO root user)
 * @param secretKey the secret key of that endpoint (MinIO root password)
 */
public record S3EndpointOverride(String endpoint, String accessKey, String secretKey) {

    public boolean active() {
        return endpoint != null && !endpoint.isBlank();
    }

    /** Points an S3 client builder at the endpoint, path-style, with its credentials; no-op if inactive. */
    public <B extends S3BaseClientBuilder<B, C>, C> B applyTo(B builder) {
        if (!active()) {
            return builder;
        }
        return builder
                .endpointOverride(URI.create(endpoint.trim()))
                .credentialsProvider(credentials())
                .serviceConfiguration(pathStyle());
    }

    /** Points a presigner builder at the endpoint, path-style, with its credentials; no-op if inactive. */
    public S3Presigner.Builder applyTo(S3Presigner.Builder builder) {
        if (!active()) {
            return builder;
        }
        return builder
                .endpointOverride(URI.create(endpoint.trim()))
                .credentialsProvider(credentials())
                .serviceConfiguration(pathStyle());
    }

    private StaticCredentialsProvider credentials() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey));
    }

    private static S3Configuration pathStyle() {
        return S3Configuration.builder().pathStyleAccessEnabled(true).build();
    }
}
