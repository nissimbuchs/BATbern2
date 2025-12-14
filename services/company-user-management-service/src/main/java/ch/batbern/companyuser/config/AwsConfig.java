package ch.batbern.companyuser.config;

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
 * Provides AWS client beans for S3 and EventBridge.
 * EventBridgeAsyncClient is provided by shared-kernel's EventBridgeConfig.
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

    @Bean
    @ConditionalOnMissingBean
    public AwsCredentialsProvider awsCredentialsProvider() {
        return DefaultCredentialsProvider.builder().build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Client s3Client(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating S3Client for region: {}", awsRegion);
        return S3Client.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }

    @Bean
    @ConditionalOnMissingBean
    public S3Presigner s3Presigner(S3Client s3Client) {
        log.info("Creating S3Presigner using S3Client for region: {}", awsRegion);

        // Create the presigner using the same configuration as S3Client
        return S3Presigner.create();
    }

    @Bean
    @ConditionalOnMissingBean
    public CloudWatchClient cloudWatchClient(AwsCredentialsProvider credentialsProvider) {
        log.info("Creating CloudWatchClient for region: {}", awsRegion);
        return CloudWatchClient.builder()
                .region(Region.of(awsRegion))
                .credentialsProvider(credentialsProvider)
                .build();
    }
}
