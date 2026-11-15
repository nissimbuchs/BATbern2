package ch.batbern.events.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * Local development configuration for AWS beans
 * Story 2.2: Architecture Compliance Refactoring
 * Story 2.5.3a: Added S3Client for MinIO support
 *
 * Provides mock AWS clients for local development without requiring AWS credentials
 * This configuration is active when SPRING_PROFILES_ACTIVE=local
 * - S3: Connects to local MinIO instead of AWS S3
 */
@Configuration
@Profile("local")
@Slf4j
public class LocalAwsConfig {

    /**
     * S3 Client configured for MinIO (local S3-compatible storage)
     * Story 2.5.3a: Event Theme Image Upload
     * Provides S3 operations for the upload service (Story 17.11) and the media services
     */
    @Bean
    @Primary
    public S3Client s3Client() {
        log.info("🪣 [LOCAL] Creating S3Client configured for MinIO (http://localhost:8450)");

        // Configure S3 client to use MinIO endpoint
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        AwsBasicCredentials credentials =
            AwsBasicCredentials.create("minioadmin", "minioadmin");

        StaticCredentialsProvider credentialsProvider =
            StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        URI minioEndpoint = URI.create("http://localhost:8450");

        // Build the S3Client
        S3Client s3Client = S3Client.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .endpointOverride(minioEndpoint)
            .serviceConfiguration(s3Config)
            .build();

        log.info("🪣 [LOCAL] S3Client configured successfully for MinIO");
        log.info("🪣 [LOCAL] Endpoint: http://localhost:8450");
        log.info("🪣 [LOCAL] Bucket: batbern-development-company-logos");

        return s3Client;
    }

    /**
     * S3 Presigner configured for MinIO (local S3-compatible storage)
     * Story 5.9: Session Materials Upload
     * Generates real presigned URLs that work with MinIO running on localhost:8450
     */
    @Bean
    @Primary
    public S3Presigner s3Presigner() {
        log.info("🪣 [LOCAL] Creating S3Presigner configured for MinIO (http://localhost:8450)");

        // Configure S3 presigner to use MinIO endpoint
        Region region = Region.EU_CENTRAL_1;

        // Create S3 client configuration for MinIO
        software.amazon.awssdk.services.s3.S3Configuration s3Config =
            software.amazon.awssdk.services.s3.S3Configuration.builder()
                .pathStyleAccessEnabled(true)  // MinIO requires path-style access
                .build();

        // Create credentials provider for MinIO (minioadmin/minioadmin)
        AwsBasicCredentials credentials =
            AwsBasicCredentials.create("minioadmin", "minioadmin");

        StaticCredentialsProvider credentialsProvider =
            StaticCredentialsProvider.create(credentials);

        // Create endpoint override for MinIO
        URI minioEndpoint = URI.create("http://localhost:8450");

        // Build the S3Presigner
        S3Presigner presigner = S3Presigner.builder()
            .region(region)
            .credentialsProvider(credentialsProvider)
            .endpointOverride(minioEndpoint)
            .serviceConfiguration(s3Config)
            .build();

        log.info("🪣 [LOCAL] S3Presigner configured successfully for MinIO");

        return presigner;
    }
}
