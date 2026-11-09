package ch.demo.config;

import ch.demo.service.EmailService;
import java.net.http.HttpClient;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

import static org.mockito.Mockito.mock;

// Counted (be-mock-in-process 3): EmailService and CognitoIntegrationService inside the config,
// SlugService in the nested @Configuration; UserApiClient as an S2S client (be-mock-s2s-client 1).
// Not counted: S3Client (AWS SDK), HttpClient (JDK), the mock in the helper class below (not a
// configuration), and the commented one.
@TestConfiguration
public class TestAwsConfig {

    @Bean
    @Primary
    public EmailService emailService() {
        return Mockito.mock(EmailService.class);
    }

    @Bean
    public CognitoIntegrationService cognito() {
        return mock(ch.demo.service.CognitoIntegrationService.class);
    }

    @Bean
    public S3Client s3() {
        return mock(S3Client.class);
    }

    @Bean
    public HttpClient http() {
        return mock(HttpClient.class);
    }

    @Bean
    public UserApiClient users() {
        return mock(UserApiClient.class);
    }

    // EmailService commented = mock(EmailService.class);

    @Configuration
    static class Nested {
        @Bean
        SlugService slugs() {
            return mock(SlugService.class);
        }
    }
}

class NotAConfig {
    EmailService plain = mock(EmailService.class);
}
