package ch.batbern.companyuser.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

/**
 * AWS Cognito configuration
 * Story 1.14-2 Task 12: Cognito Integration
 * AC2: Cognito sync on user create/update
 */
@Configuration
public class CognitoConfig {

    @Value("${aws.region}")
    private String awsRegion;

    /**
     * Production Cognito client
     * Uses default credentials provider chain (IAM roles in ECS)
     */
    @Bean
    @Profile("!test")
    public CognitoIdentityProviderClient cognitoIdentityProviderClient() {
        return CognitoIdentityProviderClient.builder()
                .region(Region.of(awsRegion))
                .build();
    }
}
