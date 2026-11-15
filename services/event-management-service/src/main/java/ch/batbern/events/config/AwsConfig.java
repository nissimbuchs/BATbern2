package ch.batbern.events.config;

import ch.batbern.shared.config.S3EndpointOverride;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudfront.CloudFrontClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS SDK configuration
 * Story 2.5.3a: Event Theme Image Upload
 *
 * Note: This configuration is NOT active for 'local' profile.
 * LocalAwsConfig provides MinIO-configured beans for local development.
 */
@Configuration
@Profile("!local")
@Slf4j
public class AwsConfig {

    /** Pre-deploy gate only (S3 stand-in, S3Mock in the gate); blank in production, where CDK never sets it. */
    private final S3EndpointOverride s3EndpointOverride;

    public AwsConfig(@Value("${aws.s3.endpoint:}") String s3Endpoint,
                     @Value("${aws.s3.endpoint-access-key:}") String s3AccessKey,
                     @Value("${aws.s3.endpoint-secret-key:}") String s3SecretKey) {
        this.s3EndpointOverride = new S3EndpointOverride(s3Endpoint, s3AccessKey, s3SecretKey);
    }

    /**
     * S3 client for file operations (Production)
     * Used by the upload service (Story 17.11) and the media services to copy/delete files
     */
    @Bean
    public S3Client s3Client() {
        log.info("Creating S3Client for AWS region: eu-central-1");
        return s3EndpointOverride.applyTo(S3Client.builder()
                .region(Region.EU_CENTRAL_1) // Bern, Switzerland region
                .credentialsProvider(DefaultCredentialsProvider.builder().build()))
                .build();
    }

    /**
     * CloudFront client for CDN cache invalidation (Production)
     * Story BAT-16 (AC6): CDN Cache Invalidation
     * Used by CdnInvalidationService to invalidate CloudFront cache
     */
    @Bean
    public CloudFrontClient cloudFrontClient() {
        log.info("Creating CloudFrontClient for AWS");
        return CloudFrontClient.builder()
                .region(Region.AWS_GLOBAL) // CloudFront is a global service
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    /**
     * S3 Presigner for generating presigned URLs (Production)
     * Story 5.9: Session Materials Upload
     * Used by SessionMaterialsService to generate presigned upload URLs
     */
    @Bean
    public S3Presigner s3Presigner() {
        log.info("Creating S3Presigner for AWS region: eu-central-1");
        return s3EndpointOverride.applyTo(S3Presigner.builder()
                .region(Region.EU_CENTRAL_1) // Bern, Switzerland region
                .credentialsProvider(DefaultCredentialsProvider.builder().build()))
                .build();
    }
}
