package ch.batbern.migration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

/**
 * AWS S3 Configuration
 *
 * Configures S3 client for file uploads (presentations, photos, logos).
 * Story: 3.2.1 - Migration Tool Implementation, AC 13-16
 */
@Configuration
public class S3Configuration {

    @Value("${migration.s3.region:eu-central-1}")
    private String region;

    @Value("${migration.s3.endpoint:#{null}}")
    private String endpoint;

    /**
     * S3 Client for file uploads
     * Supports LocalStack for testing via endpoint override
     */
    @Bean
    public S3Client s3Client() {
        var builder = S3Client.builder()
            .region(Region.of(region))
            .credentialsProvider(DefaultCredentialsProvider.create());

        // Override endpoint for LocalStack testing
        if (endpoint != null && !endpoint.isEmpty()) {
            builder.endpointOverride(URI.create(endpoint));
            builder.forcePathStyle(true);  // Required for LocalStack
        }

        return builder.build();
    }
}
