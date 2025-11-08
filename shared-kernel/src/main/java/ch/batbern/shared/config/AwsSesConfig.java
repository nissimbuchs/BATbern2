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
 * In production/staging: Uses AWS IAM roles
 * In local development: Uses LocalStack or disabled
 *
 * Story 2.2a Task B12: Email service infrastructure
 */
@Configuration
@Slf4j
public class AwsSesConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    /**
     * AWS SES Client for production and staging environments.
     * Uses IAM roles for authentication.
     */
    @Bean
    @Profile({"!local & !test"})
    public SesClient sesClient() {
        log.info("Initializing AWS SES client for region: {}", awsRegion);

        return SesClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(DefaultCredentialsProvider.create())
                .build();
    }

    /**
     * Mock SES Client for local development and testing.
     * Prevents actual email sending in non-production environments.
     */
    @Bean
    @Profile({"local | test"})
    public SesClient mockSesClient() {
        log.info("Using mock SES client for local/test environment");

        // In local/test, we return null and handle gracefully in EmailService
        return null;
    }
}
