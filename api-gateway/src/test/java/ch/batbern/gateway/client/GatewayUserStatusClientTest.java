package ch.batbern.gateway.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link GatewayUserStatusClient} — Story 12.2.
 * Verifies the active/inactive parse, the JWT header propagation, and the fail-open contract
 * (404/no-body → empty; transient error → {@link GatewayUserStatusException}).
 */
@ExtendWith(MockitoExtension.class)
class GatewayUserStatusClientTest {

    @Mock
    private RestTemplate restTemplate;

    private GatewayUserStatusClient client;

    @BeforeEach
    void setUp() {
        client = new GatewayUserStatusClient(restTemplate, "http://cums:8085");
    }

    private ResponseEntity<UserStatusResponse> okWithActive(Boolean active) {
        UserStatusResponse body = new UserStatusResponse();
        body.setActive(active);
        return ResponseEntity.ok(body);
    }

    @Test
    void should_returnActiveTrue_when_cumsReportsActive() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenReturn(okWithActive(true));

        assertThat(client.getActiveStatus("john.doe", "tok")).contains(true);
    }

    @Test
    void should_returnActiveFalse_when_cumsReportsDeactivated() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenReturn(okWithActive(false));

        assertThat(client.getActiveStatus("jane.doe", "tok")).contains(false);
    }

    @Test
    void should_propagateBearerToken_when_lookingUpStatus() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenReturn(okWithActive(true));

        client.getActiveStatus("john.doe", "my-jwt");

        ArgumentCaptor<HttpEntity<Void>> captor = ArgumentCaptor.forClass(HttpEntity.class);
        org.mockito.Mockito.verify(restTemplate)
                .exchange(anyString(), eq(HttpMethod.GET), captor.capture(), eq(UserStatusResponse.class));
        assertThat(captor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION)).isEqualTo("Bearer my-jwt");
    }

    @Test
    void should_returnEmpty_when_bodyHasNoActiveFlag() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenReturn(okWithActive(null));

        assertThat(client.getActiveStatus("john.doe", "tok")).isEmpty();
    }

    @Test
    void should_returnEmpty_when_cumsReturns404() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenThrow(HttpClientErrorException.create(
                        HttpStatus.NOT_FOUND, "Not Found", HttpHeaders.EMPTY, new byte[0], null));

        assertThat(client.getActiveStatus("ghost", "tok")).isEmpty();
    }

    @Test
    void should_throwGatewayUserStatusException_when_cumsReturns5xx() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenThrow(HttpServerErrorException.create(
                        HttpStatus.INTERNAL_SERVER_ERROR, "Boom", HttpHeaders.EMPTY, new byte[0], null));

        assertThatThrownBy(() -> client.getActiveStatus("john.doe", "tok"))
                .isInstanceOf(GatewayUserStatusException.class);
    }

    @Test
    void should_throwGatewayUserStatusException_when_connectionFails() {
        when(restTemplate.exchange(anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(UserStatusResponse.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThatThrownBy(() -> client.getActiveStatus("john.doe", "tok"))
                .isInstanceOf(GatewayUserStatusException.class);
    }
}
