package ch.batbern.partners.config;

import ch.batbern.shared.service.EmailService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;

import static org.mockito.Mockito.mock;

/**
 * Test configuration for AWS beans
 * Provides mock AWS clients for integration tests. Domain events run through the real
 * in-process publisher (Story 17.2 part B).
 */
@TestConfiguration
@Profile("test")
public class TestAwsConfig {

    /**
     * Mock EmailService for testing email functionality
     * Provides mock shared-kernel EmailService for integration tests
     */
    @Bean
    @Primary
    public EmailService emailService() {
        EmailService mockEmailService = mock(EmailService.class);
        // sendEmail is void, no need to configure
        return mockEmailService;
    }
}
