package ch.batbern.gateway.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

/**
 * Minimal client that resolves a caller's account {@code active} status from the
 * company-user-management-service (CUMS), for the {@link
 * ch.batbern.gateway.security.AccountActiveFilter} is_active gate (Story 12.2).
 *
 * <p>The api-gateway had no user client before this story (verified 2026-06-01: no
 * {@code UserServiceClient} under {@code api-gateway/src/main/java}). This is the minimal
 * addition — it reuses the shared {@link RestTemplate} bean ({@code WebClientConfig}) and the
 * same {@code services.company-user-management.url} property {@code DomainRouter} routes with,
 * and forwards the caller's JWT so CUMS authorises the {@code /users/{username}} read.
 *
 * <p>Failure contract (the gate fails open — see {@link GatewayUserStatusException}):
 * <ul>
 *   <li>200 with an {@code active} value → {@code Optional.of(active)}</li>
 *   <li>200 with no/null {@code active}, or 404 (user not yet provisioned) →
 *       {@code Optional.empty()} (unknown → caller allows, no metric)</li>
 *   <li>timeout / connection error / 5xx / other 4xx → throws {@link
 *       GatewayUserStatusException} (caller allows + emits the {@code cums_error} metric)</li>
 * </ul>
 */
@Component
@Slf4j
public class GatewayUserStatusClient {

    private final RestTemplate restTemplate;
    private final String companyUserManagementUrl;

    public GatewayUserStatusClient(
            RestTemplate restTemplate,
            @Value("${services.company-user-management.url:http://localhost:8085}")
            String companyUserManagementUrl) {
        this.restTemplate = restTemplate;
        this.companyUserManagementUrl = companyUserManagementUrl;
    }

    /**
     * Resolve the {@code active} flag for {@code username} from CUMS, forwarding {@code
     * bearerToken} (the caller's JWT, no "Bearer " prefix) so CUMS authorises the read.
     *
     * @return present(true)=active, present(false)=deactivated, empty=unknown (404 / no body)
     * @throws GatewayUserStatusException on a transient/unexpected failure (fail-open at caller)
     */
    public Optional<Boolean> getActiveStatus(String username, String bearerToken) {
        String url = UriComponentsBuilder
                .fromHttpUrl(companyUserManagementUrl + "/api/v1/users/{username}")
                .buildAndExpand(username)
                .toUriString();

        try {
            HttpHeaders headers = new HttpHeaders();
            if (bearerToken != null && !bearerToken.isBlank()) {
                headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            HttpEntity<Void> request = new HttpEntity<>(headers);

            ResponseEntity<UserStatusResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, request, UserStatusResponse.class);

            UserStatusResponse body = response.getBody();
            if (body == null || body.getActive() == null) {
                log.debug("CUMS user-status lookup for {} returned no active flag — treating as unknown", username);
                return Optional.empty();
            }
            return Optional.of(body.getActive());

        } catch (HttpClientErrorException.NotFound e) {
            // User not provisioned (e.g. fresh federated identity, or sub-only lookup) — unknown,
            // fail-open (allow); JIT provisioning will create the row. Not an error.
            log.debug("CUMS user-status lookup for {} returned 404 — not provisioned, fail-open", username);
            return Optional.empty();

        } catch (Exception e) {
            // Timeout / connection error / 5xx / other 4xx — transient/unexpected. Surface as a
            // checked failure so the filter fails open AND records the cums_error metric.
            throw new GatewayUserStatusException(
                    "CUMS user-status lookup failed for " + username + ": " + e.getMessage(), e);
        }
    }
}
