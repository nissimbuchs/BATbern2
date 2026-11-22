package ch.batbern.shared.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.sesv2.SesV2Client;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optional SES endpoint override (pre-deploy gate, P5 spike): set, the real {@code SesV2Client}
 * talks to a fake SES; blank or absent, as in production, the client is built exactly as before.
 */
class AwsSesConfigTest {

    @Test
    @DisplayName("should_pointClientAtFakeSes_when_endpointSet")
    void should_pointClientAtFakeSes_when_endpointSet() {
        try (SesV2Client client = AwsSesConfig.buildSesClient("eu-central-1", "http://fake-ses:8025")) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://fake-ses:8025"));
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(StaticCredentialsProvider.class);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("should_buildClientAsInProduction_when_endpointBlank")
    void should_buildClientAsInProduction_when_endpointBlank(String endpoint) {
        try (SesV2Client client = AwsSesConfig.buildSesClient("eu-central-1", endpoint)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(DefaultCredentialsProvider.class);
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("eu-central-1");
        }
    }
}
