package ch.batbern.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * AWS SES configuration for sending transactional emails.
 *
 * Production/Staging/Local: Uses real AWS SES (sandbox mode in dev)
 * Test: Uses mock SES client
 *
 * Story 2.2a Task B12: Email service infrastructure
 */
@Configuration
@Slf4j
public class AwsSesConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    @Value("${aws.profile:#{null}}")
    private String awsProfile;

    /**
     * AWS SES Client for production, staging, and local development.
     * Local development uses AWS dev account in sandbox mode.
     * Uses DefaultCredentialsProvider which supports:
     * - IAM roles (production/staging ECS tasks)
     * - Named profiles (local development)
     * - Environment variables
     */
    @Bean
    @Profile("!test")
    public SesClient sesClient() {
        log.info("Initializing AWS SES client for region: {}, profile: {}",
                awsRegion, awsProfile != null ? awsProfile : "default");

        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .build();
    }

    /**
     * Mock SES Client for test environment only.
     * Returns null and EmailService handles it gracefully by logging instead of sending.
     */
    @Bean
    @Profile("test")
    public SesClient testSesClient() {
        log.info("Using mock SES client for test environment");
        // Return null - EmailService checks for null and logs instead of sending
        return null;
    }
}
