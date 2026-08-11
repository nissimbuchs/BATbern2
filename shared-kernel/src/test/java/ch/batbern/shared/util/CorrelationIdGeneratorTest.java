package ch.batbern.shared.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for CorrelationIdGenerator.
 *
 * <p>Issue #904: every 500 handed the caller a {@code correlationId} that appeared in no log
 * line, because handlers logged first and called {@code generate()} second — and
 * {@code generate()} mints a fresh UUID per call. These tests pin the contract that makes the
 * ID in the response the same ID the logs are tagged with.
 */
@DisplayName("CorrelationIdGenerator Unit Tests")
class CorrelationIdGeneratorTest {

    @BeforeEach
    @AfterEach
    void clearMdc() {
        // Request threads are pooled, so a leaked MDC entry would bleed across tests
        // exactly as it would bleed across requests.
        MDC.remove(CorrelationIdGenerator.MDC_KEY);
    }

    @Test
    @DisplayName("should_returnSameId_when_currentCalledTwiceInSameThread")
    void should_returnSameId_when_currentCalledTwiceInSameThread() {
        // Given: no correlation ID established yet
        assertThat(MDC.get(CorrelationIdGenerator.MDC_KEY)).isNull();

        // When: two callers within one request ask for the ID
        String first = CorrelationIdGenerator.current();
        String second = CorrelationIdGenerator.current();

        // Then: they agree — one request has one ID
        assertThat(first).isNotBlank();
        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("should_registerIdInMdc_when_currentCalledFirstTime")
    void should_registerIdInMdc_when_currentCalledFirstTime() {
        // When: the ID is obtained
        String correlationId = CorrelationIdGenerator.current();

        // Then: it is visible to the logging framework, so a %X{correlationId} pattern and an
        // explicit log argument both render the value the client was given.
        assertThat(MDC.get(CorrelationIdGenerator.MDC_KEY)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("should_reuseUpstreamId_when_alreadyPresentInMdc")
    void should_reuseUpstreamId_when_alreadyPresentInMdc() {
        // Given: an ID established upstream — by a correlation filter, or forwarded from
        // another service via X-Correlation-ID — so one client request keeps one ID across hops
        String upstream = "11111111-2222-3333-4444-555555555555";
        MDC.put(CorrelationIdGenerator.MDC_KEY, upstream);

        // When/Then: it is reused, never replaced
        assertThat(CorrelationIdGenerator.current()).isEqualTo(upstream);
    }

    @Test
    @DisplayName("should_generateFreshId_when_mdcValueIsBlank")
    void should_generateFreshId_when_mdcValueIsBlank() {
        // Given: a blank header value made it into the MDC
        MDC.put(CorrelationIdGenerator.MDC_KEY, "   ");

        // When: the ID is obtained
        String correlationId = CorrelationIdGenerator.current();

        // Then: a usable ID replaces the blank, and the MDC is corrected
        assertThat(correlationId).isNotBlank();
        assertThat(correlationId).isNotEqualTo("   ");
        assertThat(MDC.get(CorrelationIdGenerator.MDC_KEY)).isEqualTo(correlationId);
    }

    @Test
    @DisplayName("should_returnDistinctIds_when_generateCalledRepeatedly")
    void should_returnDistinctIds_when_generateCalledRepeatedly() {
        // generate() keeps its old semantics — a fresh ID per call — because the correlation
        // filters legitimately use it to mint the ID for a brand-new request.
        assertThat(CorrelationIdGenerator.generate())
                .isNotEqualTo(CorrelationIdGenerator.generate());
    }

    @Test
    @DisplayName("should_isolateIds_when_usedFromDifferentThreads")
    void should_isolateIds_when_usedFromDifferentThreads() throws Exception {
        // Given: this thread's ID
        String main = CorrelationIdGenerator.current();

        // When: another request thread obtains one
        String[] other = new String[1];
        Thread t = new Thread(() -> other[0] = CorrelationIdGenerator.current());
        t.start();
        t.join();

        // Then: concurrent requests do not share an ID (MDC is thread-scoped)
        assertThat(other[0]).isNotBlank();
        assertThat(other[0]).isNotEqualTo(main);
    }
}
