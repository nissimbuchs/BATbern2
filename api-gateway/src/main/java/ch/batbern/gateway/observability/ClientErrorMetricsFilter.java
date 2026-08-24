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
 * <h2>Authenticated traffic only (#995)</h2>
 *
 * <p>A request that presented <b>no credentials</b> is excluded from BOTH metrics. A 401 with no
 * {@code Authorization} header is the security boundary working correctly — it is not a defect and
 * there is nothing to act on. A 401 on a request that <i>did</i> carry a token is the real signal:
 * a broken deploy, a rotated key, a regressed JWT converter.
 *
 * <p>This was learned the hard way on 2026-08-24. Our own scheduled OWASP ZAP scan
 * ({@code security-scan.yml}, Mondays, targeting api.batbern.ch) drove <b>59,039</b>
 * unauthenticated 4xx between 03:35 and 03:50 UTC — a 5,000-line sample was 4,997× 401 — taking
 * the ratio to 88% and paging at 03:49. ZAP builds its requests from our OpenAPI specs, which is
 * why the logged paths carried unsubstituted placeholders like
 * {@code /api/v1/events/eventCode/sessions/sessionSlug/timing}. Same category as the {@code .php}
 * sweep that killed {@code alb-4xx}, only originating inside the house.
 *
 * <p><b>Both sides, not just the numerator.</b> Excluding credential-less requests from the error
 * count while leaving them in the request count would drive the ratio toward zero during a scan
 * and mask a genuine auth break happening at the same time — the alarm would go quiet exactly
 * when it mattered most. A test pins this.
 *
 * <p><b>Accepted coverage loss.</b> Public endpoints ({@code /api/v1/public/*}, config,
 * unsubscribe, verification) legitimately carry no credentials, so a 4xx regression on one is
 * invisible to this alarm. Closing that needs a second, separate counter for public-path errors;
 * deliberately not built until there is a reason to.
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

    private static final String AUTHORIZATION_HEADER = "Authorization";

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

        // #995: no credentials, no metric — neither side. See the class note.
        if (!presentedCredentials(request)) {
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

    /**
     * Whether the caller presented credentials at all.
     *
     * <p>Only the presence of a non-blank {@code Authorization} header, deliberately: this asks
     * "did someone claim an identity", not "was the claim any good". A malformed or expired token
     * IS a credential — we were asked to authenticate it and refused, which is precisely the
     * signal worth alarming on.
     */
    private boolean presentedCredentials(HttpServletRequest request) {
        String authorization = request.getHeader(AUTHORIZATION_HEADER);
        return authorization != null && !authorization.isBlank();
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
