package ch.demo;

import ch.demo.client.UserApiClient;
import ch.demo.client.CompanyServiceClient;
import ch.demo.mail.EmailService;
import java.time.Clock;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * Javadoc mentions are not counted: {@code @MockitoBean UserApiClient}, @Transactional.
 */
@Transactional
class DemoIntegrationTest extends AbstractIntegrationTest {

    // @MockitoBean private UserApiClient commentedOut;

    @MockitoBean
    private UserApiClient userApiClient;

    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private EmailService emailService;

    @MockitoBean
    @Qualifier("company")
    private CompanyServiceClient companyServiceClient;

    @MockitoBean private SesClient sesClient;

    @MockitoBean
    private software.amazon.awssdk.services.s3.S3Client s3Client;

    @MockitoBean
    private SecretsManagerClient secretsManagerClient;

    @MockitoBean
    private Clock clock;

    private static final String NOT_CODE = "@MockitoBean private UserApiClient inString;";

    private static final String BLOCK = """
        @MockitoBean
        private UserApiClient inTextBlock;
        """;
}
