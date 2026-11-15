package ch.batbern.companyuser.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClientBuilder;

import java.net.URI;

/**
 * AWS Cognito configuration
 * Story 1.14-2 Task 12: Cognito Integration
 * AC2: Cognito sync on user create/update
 */
@Configuration
@Slf4j
public class CognitoConfig {

    /** Placeholder credentials for a stand-in pool: it ignores the signature, Cognito never sees them. */
    private static final String OVERRIDE_CREDENTIAL = "endpoint-override";

    @Value("${aws.region}")
    private String awsRegion;

    /**
     * Optional Cognito endpoint (env {@code BATBERN_COGNITO_ENDPOINT}), for the pre-deploy gate's
     * stand-in pool. Blank by default and never set by the CDK stacks, so production is unchanged.
     */
    @Value("${batbern.cognito.endpoint:}")
    private String cognitoEndpoint;

    /**
     * Production Cognito client
     * Uses default credentials provider chain (IAM roles in ECS)
     */
    @Bean
    @Profile("!test")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return buildCognitoClient(awsRegion, cognitoEndpoint);
    }

    /**
     * Builds the client. With an endpoint, it talks to that endpoint with placeholder credentials
     * (a stand-in pool); without one, it is the production client: Cognito, default credentials chain.
     */
    static CognitoIdentityProviderClient buildCognitoClient(String region, String endpoint) {
        CognitoIdentityProviderClientBuilder builder = CognitoIdentityProviderClient.builder()
                .region(Region.of(region));
        if (endpoint == null || endpoint.isBlank()) {
            return builder.build();
        }
        log.warn("Cognito endpoint override active: {} (pre-deploy gate only, never production)", endpoint.trim());
        return builder
                .endpointOverride(URI.create(endpoint.trim()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(OVERRIDE_CREDENTIAL, OVERRIDE_CREDENTIAL)))
                .build();
    }
}
