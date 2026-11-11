package ch.batbern.shared.contract;

import ch.batbern.shared.security.S2sHeaders;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Calls a contract the calling service owns (ADR-016 decision 4): an {@code /internal} operation
 * specified in the caller's own internal spec and served by every implementer listed in its
 * configuration under {@code batbern.contracts.<name>.implementers} (base URLs, a YAML list or
 * comma-separated). The caller names no implementing service in code; this is the one way a
 * service calls a contract it owns.
 *
 * <p>Every call goes to each implementer in configured order, with the headers of
 * {@link S2sHeaders#forCurrentUser()} (the user's JWT when there is one, never required). The first
 * implementer that fails stops the call: a 409 becomes {@link ContractRefusedException} with the
 * implementer's {@code message}, anything else (another status, no answer) becomes
 * {@link ContractCallFailedException}. Whether a failure fails the caller's action is the caller's
 * decision.
 */
@Slf4j
public final class ContractCalls {

    /** Configuration prefix: {@code batbern.contracts.<name>.implementers}. */
    public static final String PROPERTY_PREFIX = "batbern.contracts.";

    private final String name;
    private final List<String> implementers;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * @param name         the contract's configuration name, e.g. {@code mail-center-requests}
     * @param implementers base URLs of the implementers (a trailing {@code /} is dropped)
     * @param restTemplate the caller's template, with its timeouts
     */
    public ContractCalls(String name, List<String> implementers, RestTemplate restTemplate) {
        this.name = name;
        this.implementers = implementers.stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.endsWith("/") ? s.substring(0, s.length() - 1) : s)
                .toList();
        this.restTemplate = restTemplate;
        if (this.implementers.isEmpty()) {
            log.warn("CONTRACT_CALL no implementers contract={}: calls go nowhere; set {}{}.implementers", name,
                    PROPERTY_PREFIX, name);
        }
    }

    /** The implementers configured under {@code batbern.contracts.<name>.implementers}; none when unset. */
    public static ContractCalls fromEnvironment(String name, Environment environment, RestTemplate restTemplate) {
        List<String> implementers = Binder.get(environment)
                .bind(PROPERTY_PREFIX + name + ".implementers", Bindable.listOf(String.class))
                .orElse(List.of());
        return new ContractCalls(name, implementers, restTemplate);
    }

    public String name() {
        return name;
    }

    /** Base URLs of the implementers, in call order. */
    public List<String> implementers() {
        return implementers;
    }

    /**
     * {@code POST}s {@code body} (JSON) to {@code path} at every implementer; the answer body is
     * ignored. {@code path} may be a template whose variables are filled from {@code uriVariables}
     * and encoded.
     */
    public void post(String path, Object body, Object... uriVariables) {
        for (String implementer : implementers) {
            URI uri = UriComponentsBuilder.fromUriString(implementer + path).encode().buildAndExpand(uriVariables)
                    .toUri();
            HttpHeaders headers = S2sHeaders.forCurrentUser();
            headers.setContentType(MediaType.APPLICATION_JSON);
            call(implementer, HttpMethod.POST, () ->
                    restTemplate.exchange(uri, HttpMethod.POST, new HttpEntity<>(body, headers), Void.class));
        }
    }

    /** {@code GET}s {@code path} at every implementer; the answers in implementer order. */
    public <T> List<T> get(String path, Map<String, ?> uriVariables, Class<T> type) {
        List<T> answers = new ArrayList<>();
        for (String implementer : implementers) {
            URI uri = UriComponentsBuilder.fromUriString(implementer + path).encode().buildAndExpand(uriVariables)
                    .toUri();
            HttpHeaders headers = S2sHeaders.forCurrentUser();
            headers.setAccept(List.of(MediaType.APPLICATION_JSON));
            answers.add(call(implementer, HttpMethod.GET, () ->
                    restTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), type).getBody()));
        }
        return answers;
    }

    private <T> T call(String implementer, HttpMethod method, Supplier<T> request) {
        try {
            return request.get();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            if (status == 409) {
                throw new ContractRefusedException(name, implementer, messageOf(e.getResponseBodyAsString()));
            }
            log.warn("CONTRACT_CALL failed contract={} implementer={} method={} status={}", name, implementer,
                    method, status);
            throw new ContractCallFailedException(name, implementer, e);
        } catch (RestClientException e) {
            log.warn("CONTRACT_CALL failed contract={} implementer={} method={} cause={}", name, implementer,
                    method, e.getClass().getSimpleName());
            throw new ContractCallFailedException(name, implementer, e);
        }
    }

    /** The {@code message} of an {@code ErrorResponse} body, else the raw body. */
    private String messageOf(String body) {
        if (body == null || body.isBlank()) {
            return "refused";
        }
        try {
            return objectMapper.readTree(body).path("message").asText(body);
        } catch (Exception e) {
            return body;
        }
    }
}
