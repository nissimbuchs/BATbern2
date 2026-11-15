package ch.batbern.companyuser.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

import java.net.URI;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The optional Cognito endpoint override (pre-deploy gate, P5 spike): set, the admin calls go to a
 * stand-in pool; blank or absent, as in production, the client is built exactly as before.
 */
class CognitoConfigTest {

    @Test
    @DisplayName("should_pointClientAtStandIn_when_endpointSet")
    void should_pointClientAtStandIn_when_endpointSet() {
        try (CognitoIdentityProviderClient client =
                 CognitoConfig.buildCognitoClient("eu-central-1", "http://fake-aws:8025")) {
            assertThat(client.serviceClientConfiguration().endpointOverride())
                .contains(URI.create("http://fake-aws:8025"));
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isInstanceOf(StaticCredentialsProvider.class);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"  "})
    @DisplayName("should_buildClientAsInProduction_when_endpointBlank")
    void should_buildClientAsInProduction_when_endpointBlank(String endpoint) {
        try (CognitoIdentityProviderClient client = CognitoConfig.buildCognitoClient("eu-central-1", endpoint)) {
            assertThat(client.serviceClientConfiguration().endpointOverride()).isEmpty();
            assertThat(client.serviceClientConfiguration().credentialsProvider())
                .isNotInstanceOf(StaticCredentialsProvider.class);
            assertThat(client.serviceClientConfiguration().region().id()).isEqualTo("eu-central-1");
        }
    }
}
