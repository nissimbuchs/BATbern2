package ch.batbern.partners.config;

import ch.batbern.shared.test.mail.SesRecorder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Test configuration for AWS beans
 * Provides mock AWS clients for integration tests. Domain events run through the real
 * in-process publisher (Story 17.2 part B).
 */
@TestConfiguration
@Profile("test")
public class TestAwsConfig {

    /**
     * Story 17.0b3: the real {@code EmailService} runs in every integration test, against this
     * recording {@code SesClient} (nothing is sent). Tests autowire {@link SesRecorder}, reset it in
     * {@code @BeforeEach} and assert on what SES received, never on {@code EmailService} calls.
     */
    @Bean
    public SesRecorder sesRecorder() {
        return new SesRecorder();
    }

    /** The context's only SES client: the recorder's. */
    @Bean
    @Primary
    public SesClient sesRecorderClient(SesRecorder sesRecorder) {
        return sesRecorder.client();
    }
}
