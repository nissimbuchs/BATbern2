package ch.batbern.events.config;

import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * #1186: an integration test that imports {@link TestAwsConfig} must get its mocked S3 clients, never
 * the real SDK clients from {@code AwsConfig}. A real client would call AWS from the test JVM.
 */
@SpringBootTest
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("TestAwsConfig provides the S3 clients")
class TestAwsConfigGuardTest extends AbstractIntegrationTest {

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Test
    @DisplayName("should_injectMockedS3Clients_when_testAwsConfigImported")
    void should_injectMockedS3Clients_when_testAwsConfigImported() {
        assertThat(Mockito.mockingDetails(s3Client).isMock()).as("S3Client is a Mockito mock").isTrue();
        assertThat(Mockito.mockingDetails(s3Presigner).isMock()).as("S3Presigner is a Mockito mock").isTrue();
    }
}
