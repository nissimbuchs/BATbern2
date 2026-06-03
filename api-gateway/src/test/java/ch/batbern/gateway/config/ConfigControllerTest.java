package ch.batbern.gateway.config;

import ch.batbern.gateway.config.dto.FrontendConfigDTO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ConfigController} — focused on the runtime-config feature-flag
 * contract served at {@code GET /api/v1/config}. Story 12.9 adds {@code features.sso}.
 *
 * <p>Plain unit test (no Spring context / security chain needed): the controller's
 * {@code @Value} fields are set via {@link ReflectionTestUtils}, mirroring the slice-free
 * style used elsewhere in this package. The {@code sso} flag is a pure DTO/serialization
 * concern, so no Testcontainers / DB are involved.
 */
class ConfigControllerTest {

    private ConfigController controller;

    @BeforeEach
    void setUp() {
        controller = new ConfigController();
        ReflectionTestUtils.setField(controller, "environment", "staging");
        ReflectionTestUtils.setField(controller, "cognitoUserPoolId", "eu-central-1_TEST");
        ReflectionTestUtils.setField(controller, "cognitoClientId", "test-client-id");
        ReflectionTestUtils.setField(controller, "awsRegion", "eu-central-1");
        ReflectionTestUtils.setField(controller, "serverPort", 8080);
        // TurnstileProperties is a plain @Data bean — a default instance (enabled=false) is enough.
        ReflectionTestUtils.setField(controller, "turnstileProperties", new TurnstileProperties());
    }

    @Test
    void should_defaultSsoFlagFalse_when_featuresSsoEnabledUnset() {
        ReflectionTestUtils.setField(controller, "ssoEnabled", false);

        ResponseEntity<FrontendConfigDTO> response = controller.getConfig();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFeatures()).isNotNull();
        assertThat(response.getBody().getFeatures().isSso()).isFalse();
    }

    @Test
    void should_serveSsoFlagTrue_when_featuresSsoEnabledTrue() {
        ReflectionTestUtils.setField(controller, "ssoEnabled", true);

        ResponseEntity<FrontendConfigDTO> response = controller.getConfig();

        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getFeatures().isSso()).isTrue();
    }
}
