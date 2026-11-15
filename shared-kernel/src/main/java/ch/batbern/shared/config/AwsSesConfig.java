package ch.batbern.shared.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.SesClientBuilder;

import java.net.URI;

/**
 * AWS SES configuration for sending transactional emails.
 *
 * Production/Staging/Local: Uses real AWS SES (sandbox mode in dev)
 * Test: the test fixtures' SesRecorder provides the SesClient (17.0b3)
 *
 * Story 2.2a Task B12: Email service infrastructure
 */
@Configuration
@Slf4j
public class AwsSesConfig {

    /** Placeholder credentials for a fake SES: it ignores the signature, real SES never sees them. */
    private static final String OVERRIDE_CREDENTIAL = "endpoint-override";

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    @Value("${aws.profile:#{null}}")
    private String awsProfile;

    /**
     * Optional SES endpoint (env {@code BATBERN_SES_ENDPOINT}), for the pre-deploy gate's fake SES.
     * Blank by default and never set by the CDK stacks, so production builds the client as before.
     */
    @Value("${batbern.ses.endpoint:}")
    private String sesEndpoint;

    /**
     * AWS SES Client for production and staging only.
     * Local development uses no SesClient — EmailService captures emails in-memory via LocalEmailCapture.
     * Integration tests get theirs from the test fixtures' {@code SesRecorder} (17.0b3), never from here.
     * Uses DefaultCredentialsProvider which supports:
     * - IAM roles (production/staging ECS tasks)
     * - Named profiles (local development)
     * - Environment variables
     */
    @Bean
    @Profile("!test & !local")
    public SesClient sesClient() {
        log.info("Initializing AWS SES client for region: {}, profile: {}",
                awsRegion, awsProfile != null ? awsProfile : "default");
        return buildSesClient(awsRegion, sesEndpoint);
    }

    /**
     * Builds the client. With an endpoint, it talks to that endpoint with placeholder credentials
     * (a fake SES); without one, it is the production client: AWS SES, default credentials chain.
     */
    static SesClient buildSesClient(String region, String endpoint) {
        SesClientBuilder builder = SesClient.builder().region(Region.of(region));
        if (endpoint == null || endpoint.isBlank()) {
            return builder.credentialsProvider(DefaultCredentialsProvider.builder().build()).build();
        }
        log.warn("SES endpoint override active: {} (pre-deploy gate only, never production)", endpoint.trim());
        return builder
                .endpointOverride(URI.create(endpoint.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(OVERRIDE_CREDENTIAL, OVERRIDE_CREDENTIAL)))
                .build();
    }
}
