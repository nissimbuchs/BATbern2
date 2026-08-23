package ch.batbern.gateway.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ClientErrorMetricsFilter} — #986.
 *
 * <p>The alarm this filter feeds replaced {@code batbern-{env}-alb-4xx}, which counted every
 * target 4xx on the load balancer. That number was dominated by a PHP webshell sweep:
 * measured 2026-08-23 14:35-14:50 UTC, 384 of 808 requests went to 186 distinct nonexistent
 * {@code .php} paths, against 64 requests of real {@code /api/v1/*} traffic. The whole point
 * of this filter is that the 384 must not be counted and the 64 must be — in BOTH the error
 * count and the request count, because the alarm is a ratio of the two.
 */
class ClientErrorMetricsFilterTest {

    private MeterRegistry meterRegistry;
    private ClientErrorMetricsFilter filter;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        filter = new ClientErrorMetricsFilter(meterRegistry, List.of("/api/"));
    }

    private void run(String path, int status) throws Exception {
        run(filter, path, status);
    }

    private void run(ClientErrorMetricsFilter target, String path, int status) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", path);
        request.setRequestURI(path);
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(status);
        target.doFilter(request, response, new MockFilterChain());
    }

    private double count(String metric) {
        var counter = meterRegistry.find(metric).counter();
        return counter == null ? 0d : counter.count();
    }

    private double errors() {
        return count(ClientErrorMetricsFilter.CLIENT_ERRORS_METRIC);
    }

    private double requests() {
        return count(ClientErrorMetricsFilter.REQUESTS_METRIC);
    }

    // ── The scanner traffic that made alb-4xx useless ──────────────────────────────────

    @ParameterizedTest
    @ValueSource(strings = {
        "/gecko-new.php",
        "/aa.php",
        "/this_is_a_new_hello_world.php",
        "/wp-content/plugins/hellopress/wp_filemanager.php",
        "/qyffk.php",
        "//p4.php",
        "/",
    })
    void should_countNothing_when_pathIsNotServedByTheGateway(String scannerPath) throws Exception {
        run(scannerPath, 404);

        assertThat(errors())
                .as("scanner 404s are the entire reason alb-4xx was retired (#986)")
                .isZero();
        assertThat(requests())
                .as("they must stay out of the DENOMINATOR too, or a sweep dilutes the ratio "
                        + "and hides a real break")
                .isZero();
    }

    @Test
    void should_countNothing_when_wholeScannerSweepArrives() throws Exception {
        // The shape of the real 14:35-14:50 UTC window: a large sweep of distinct bogus
        // paths. None of it may reach either metric, at any volume.
        for (int i = 0; i < 200; i++) {
            run("/probe-" + i + ".php", 404);
        }

        assertThat(errors()).isZero();
        assertThat(requests()).isZero();
    }

    // ── The signal that must survive ───────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 409, 422, 429, 499})
    void should_countAsClientError_when_servedApiPathReturns4xx(int status) throws Exception {
        run("/api/v1/events", status);

        assertThat(errors())
                .as("a 4xx on a path the gateway serves is our defect, whatever the code")
                .isEqualTo(1d);
        assertThat(requests()).isEqualTo(1d);
    }

    @Test
    void should_produceRatioOfOne_when_authBreaksAcrossAllRealRequests() throws Exception {
        // The regression alb-4xx was reaching for: a deploy that starts 401-ing real traffic.
        // At the measured midday volume (12-35 requests per 5 minutes) an absolute threshold
        // could not see this, which is why the alarm is a ratio.
        for (int i = 0; i < 30; i++) {
            run("/api/v1/events", 401);
        }

        assertThat(errors()).isEqualTo(30d);
        assertThat(requests()).isEqualTo(30d);
    }

    @Test
    void should_produceLowRatio_when_healthyTrafficCarriesAFewClientErrors() throws Exception {
        // The nightly E2E suite's measured shape: high volume, a small share of deliberate
        // 4xx assertions (peak 32 in a 5-minute bin against 988 requests). This must not
        // approach any sane ratio threshold.
        for (int i = 0; i < 3; i++) {
            run("/api/v1/events", 404);
        }
        for (int i = 0; i < 97; i++) {
            run("/api/v1/events", 200);
        }

        assertThat(errors()).isEqualTo(3d);
        assertThat(requests()).isEqualTo(100d);
    }

    // ── Everything else stays out of the error count ───────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {200, 201, 204, 301, 302, 304})
    void should_countRequestButNotError_when_servedPathSucceeds(int status) throws Exception {
        run("/api/v1/events", status);

        assertThat(errors()).isZero();
        assertThat(requests())
                .as("successes are the denominator — the ratio is meaningless without them")
                .isEqualTo(1d);
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 502, 503, 504})
    void should_countRequestButNotError_when_servedPathReturns5xx(int status) throws Exception {
        // 5xx has its own alarm (alb-5xx) and must not be double-counted as a client error.
        run("/api/v1/events", status);

        assertThat(errors()).isZero();
        assertThat(requests()).isEqualTo(1d);
    }

    @Test
    void should_countNothing_when_healthCheckIsProbed() throws Exception {
        // /actuator/health was 360 of the 808 requests in the measured window. It is not part
        // of the API surface and must move neither side of the ratio.
        run("/actuator/health", 404);

        assertThat(errors()).isZero();
        assertThat(requests()).isZero();
    }

    // ── Behaviour of the filter itself ─────────────────────────────────────────────────

    @Test
    void should_alwaysContinueTheChain_when_invoked() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/events");
        request.setRequestURI("/api/v1/events");
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(404);
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest())
                .as("an observability filter must never be able to drop a request")
                .isSameAs(request);
    }

    @Test
    void should_honourConfiguredPrefixes_when_gatewaySurfaceChanges() throws Exception {
        ClientErrorMetricsFilter wider =
                new ClientErrorMetricsFilter(meterRegistry, List.of("/api/", "/gdpr/"));

        run(wider, "/gdpr/export", 403);

        assertThat(errors()).isEqualTo(1d);
        assertThat(requests()).isEqualTo(1d);
    }

    @Test
    void should_matchServedPrefix_when_pathCasingOrQueryStringVaries() throws Exception {
        run("/API/v1/events?filter=%7B%7D", 400);

        assertThat(errors())
                .as("prefix matching must not be defeated by casing or a query string")
                .isEqualTo(1d);
    }
}
