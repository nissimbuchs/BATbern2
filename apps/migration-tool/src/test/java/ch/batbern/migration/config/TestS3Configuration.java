package ch.batbern.migration.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * Test configuration for S3Client that points to WireMock
 */
@TestConfiguration
public class TestS3Configuration {

    @Bean
    @Primary
    public S3Client testS3Client() {
        return S3Client.builder()
            .region(Region.EU_CENTRAL_1)
            .endpointOverride(URI.create("http://localhost:8089")) // WireMock endpoint
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create("test-key", "test-secret")))
            .forcePathStyle(true) // Use path-style URLs for WireMock
            .build();
    }
}
