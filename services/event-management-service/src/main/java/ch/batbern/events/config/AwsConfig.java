package ch.batbern.events.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

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

    /**
     * S3 client for file operations (Production)
     * Used by GenericLogoService to copy/delete files
     */
    @Bean
    public S3Client s3Client() {
        log.info("Creating S3Client for AWS region: eu-central-1");
        return S3Client.builder()
                .region(Region.EU_CENTRAL_1) // Bern, Switzerland region
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }
}
