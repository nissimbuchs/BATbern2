package ch.batbern.companyuser.config;

import ch.batbern.shared.config.S3EndpointOverride;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * AWS Configuration for Company User Management Service
 *
 * Provides AWS client beans for S3 and CloudWatch.
 *
 * Note: This configuration is NOT active for 'local' profile.
 * LocalAwsConfig provides mock beans for local development.
 */
@Configuration
@org.springframework.context.annotation.Profile("!local")
@Slf4j
public class AwsConfig {

    @Value("${aws.region:eu-central-1}")
    private String awsRegion;

    /** Pre-deploy gate only (MinIO); blank in production, where the CDK stacks never set it. */
    @Value("${aws.s3.endpoint:}")
    private String s3Endpoint;

    @Value("${aws.s3.endpoint-access-key:}")
    private String s3AccessKey;

    @Value("${aws.s3.endpoint-secret-key:}")
    private String s3SecretKey;

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating S3Client for region: {}", awsRegion);
        return s3EndpointOverride().applyTo(S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider))
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(S3Client s3Client) {
        log.info("Creating S3Presigner using S3Client for region: {}", awsRegion);

        S3EndpointOverride override = s3EndpointOverride();
        if (override.active()) {
            return override.applyTo(S3Presigner.builder().region(Region.of(awsRegion))).build();
        }
        // Create the presigner using the same configuration as S3Client
        return S3Presigner.create();
    }

    private S3EndpointOverride s3EndpointOverride() {
        return new S3EndpointOverride(s3Endpoint, s3AccessKey, s3SecretKey);
    }

    @Bean
    @ConditionalOnMissingBean
    @org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "management.metrics.export.cloudwatch2.enabled",
        havingValue = "true"
    )
    public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating CloudWatchClient for region: {}", awsRegion);
        return CloudWatchClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
