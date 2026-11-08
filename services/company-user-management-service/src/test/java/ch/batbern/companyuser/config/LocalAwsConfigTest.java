package ch.batbern.companyuser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for LocalAwsConfig
 * Verifies that local AWS beans are properly configured for development
 */
@SpringBootTest(classes = LocalAwsConfig.class)
@ActiveProfiles("local")
@DisplayName("LocalAwsConfig Tests")
class LocalAwsConfigTest {

    @Autowired
    private S3Presigner s3Presigner;

    @Test
    @DisplayName("should_createS3Presigner_when_localProfileActive")
    void should_createS3Presigner_when_localProfileActive() {
        // Then
        assertThat(s3Presigner).isNotNull();
    }
}
