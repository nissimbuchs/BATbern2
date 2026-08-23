package ch.batbern.gateway.observability;

import ch.batbern.gateway.util.LogSanitizer;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Locale;

/**
 * ClientErrorMetricsFilter — an access log for the gateway's own API surface, and the 4xx
 * signal that replaced {@code batbern-{env}-alb-4xx} (#986).
 *
 * <h2>Why the old alarm had to go</h2>
 *
 * <p>{@code alb-4xx} watched {@code HTTPCode_Target_4XX_Count}, which counts every 4xx that
 * reaches a target regardless of what was asked for. Measured 2026-08-23 14:35-14:50 UTC,
 * from 808 api-gateway log lines:
 *
 * <pre>
 *   /actuator/health   360 requests    1 distinct path
 *   /api/v1/&#42;           64 requests    6 distinct paths   &lt;- all the real traffic
 *   neither            384 requests  186 distinct paths
 * </pre>
 *
 * <p>The 384 were a webshell sweep against api.batbern.ch — {@code /gecko-new.php},
 * {@code /wp-content/plugins/hellopress/wp_filemanager.php}, 186 distinct nonexistent
 * {@code .php} paths. api.batbern.ch resolves straight to the ALB with no CloudFront and no
 * WAF, so an anonymous third party could move that alarm's metric at will, and it paged six
 * times in three days without once indicating a fault.
 *
 * <h2>Why this logs every served request, not only the failures</h2>
 *
 * <p>A 4xx count alone cannot be alarmed on here, because request volume swings by a factor
 * of thirty. Measured over the 24h to 2026-08-23 15:00 UTC, {@code /api/} requests per
 * 5-minute window: 12-35 through the day, and 988 / 842 / 527 in the three windows from
 * 02:40 while the nightly E2E suite runs. Any absolute threshold is therefore either
 * unreachable at midday — a dead alarm, which is the exact failure #970 spent a PR
 * removing — or trips on the nightly suite, whose own 4xx peak measured 32 in a 5-minute
 * bin. Only a ratio survives both, and a ratio needs a denominator.
 *
 * <p>Taking that denominator from the ALB's {@code RequestCount} would fold the scanner
 * traffic back into it and dilute the very thing we are trying to isolate. So the filter
 * emits one line per served request and the ratio is computed from two metric filters over
 * that one marker. The side effect is the thing #986 actually asked for: the gateway had no
 * access log at all, which is why a 4xx spike could not be attributed without an Insights
 * archaeology session over DEBUG filter-chain chatter.
 *
 * <h2>What counts as "ours"</h2>
 *
 * <p>A prefix list, not {@link ch.batbern.gateway.routing.DomainRouter}. The router looks
 * like the authority — it throws {@code RoutingException} for anything it cannot place — but
 * it only knows *proxied* paths, so the gateway's own controllers ({@code /api/v1/config},
 * the GDPR export and deletion endpoints) would be classified as nonexistent and their
 * failures would go uncounted.
 *
 * <p>The trade is explicit: a scanner guessing {@code /api/v1/something-fake} is counted.
 * Acceptable — that requires guessing our namespace rather than spraying a wordlist of PHP
 * backdoors, and none of the 186 paths in the measured sweep would have qualified.
 * Unserved paths produce no line at all, so we also stop paying to log the sweep.
 *
 * <h2>Wiring and safety</h2>
 *
 * <p>{@code @Order(HIGHEST_PRECEDENCE)} so the filter wraps the whole chain and observes the
 * status actually sent, including the 401/403 written by Spring Security's entry point and
 * the 400s from {@code GlobalExceptionHandler}. It reads the response and never writes to
 * it, and its own failures are swallowed — an observability filter that can 500 a request is
 * worse than no observability.
 *
 * <p>The query string is deliberately dropped before logging. Token-credentialed endpoints
 * (email verification, unsubscribe, registration confirm) carry the credential in the query
 * string, and an access log is the last place it should land.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class ClientErrorMetricsFilter extends OncePerRequestFilter {

    /** Micrometer counter for served API requests (local / Prometheus visibility). */
    public static final String REQUESTS_METRIC = "gateway.api_requests";

    /** Micrometer counter for served API requests that returned 4xx. */
    public static final String CLIENT_ERRORS_METRIC = "gateway.api_client_errors";

    /**
     * Token the CloudWatch MetricFilters match on. Must stay in sync with
     * {@code infrastructure/lib/constructs/alb-alarms.ts}, where
     * {@code platform-alarms.test.ts} pins the same literals.
     */
    public static final String LOG_MARKER = "GATEWAY_API_REQUEST";

    /** Field the error-counting MetricFilter discriminates on. */
    public static final String CLIENT_ERROR_FIELD = "clientError=true";

    private final MeterRegistry meterRegistry;
    private final List<String> servedPrefixes;

    public ClientErrorMetricsFilter(
            MeterRegistry meterRegistry,
            @Value("${gateway.client-error-metrics.served-prefixes:/api/}") List<String> servedPrefixes) {
        this.meterRegistry = meterRegistry;
        this.servedPrefixes = servedPrefixes.stream()
                .map(prefix -> prefix.toLowerCase(Locale.ROOT))
                .toList();
        log.info("ClientErrorMetricsFilter initialised — servedPrefixes={}", this.servedPrefixes);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        chain.doFilter(request, response);

        // Observation only, and deliberately after the chain: the status is not final until
        // everything downstream has run.
        try {
            record(request, response);
        } catch (RuntimeException e) {
            log.warn("Failed to record API request metric", e);
        }
    }

    private void record(HttpServletRequest request, HttpServletResponse response) {
        String path = strippedPath(request.getRequestURI());
        if (!isServedByGateway(path)) {
            return;
        }

        int status = response.getStatus();
        boolean clientError = status >= 400 && status < 500;

        meterRegistry.counter(REQUESTS_METRIC).increment();
        if (clientError) {
            meterRegistry.counter(CLIENT_ERRORS_METRIC).increment();
        }

        // One line per served request. `clientError` is an explicit field rather than
        // something the metric filter has to infer from the status text, so the CloudWatch
        // pattern stays a literal-term match and cannot drift as status codes change.
        log.info("{} status={} clientError={} method={} path={}",
                LOG_MARKER,
                status,
                clientError,
                LogSanitizer.sanitize(request.getMethod()),
                LogSanitizer.sanitize(path));
    }

    /** Path without its query string — see the class note on token-credentialed endpoints. */
    private String strippedPath(String uri) {
        if (uri == null) {
            return null;
        }
        return uri.split("\\?")[0];
    }

    /**
     * Whether the path is part of the gateway's own public surface, ignoring casing. Casing
     * matters because a client is not obliged to get it right, and a 4xx we caused is still
     * ours whatever case it arrived in.
     */
    private boolean isServedByGateway(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }
        String lower = path.toLowerCase(Locale.ROOT);
        return servedPrefixes.stream().anyMatch(lower::startsWith);
    }
}
