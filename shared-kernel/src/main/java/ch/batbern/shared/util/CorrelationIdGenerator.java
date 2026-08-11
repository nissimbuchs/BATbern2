package ch.batbern.shared.util;

import org.slf4j.MDC;

import java.util.UUID;

public class CorrelationIdGenerator {
    /** Standard HTTP header name for request correlation across microservices. */
    private static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /** MDC key used by every logback pattern that prints {@code %X{correlationId}}. */
    public static final String MDC_KEY = "correlationId";

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    /**
     * Returns the correlation ID for the request currently being handled, creating and
     * registering one if this is the first caller.
     *
     * <p>Prefer this over {@link #generate()} anywhere the ID is handed to a client — an
     * error response, a response header, a support-facing message. {@code generate()} mints a
     * fresh UUID on every call, so a handler that logs first and generates second returns an
     * ID that appears in no log line, and two calls within one request disagree about the ID
     * of that request (issue #904).
     *
     * <p>The ID is read from (and written back to) the SLF4J {@link MDC} under
     * {@link #MDC_KEY}, so:
     * <ul>
     *   <li>every log statement in the request carries it, wherever the log pattern includes
     *       {@code %X{correlationId}};</li>
     *   <li>the value returned to the caller is the same value the logs were tagged with;</li>
     *   <li>an ID already established upstream — by a correlation filter, or forwarded from
     *       another service via the {@code X-Correlation-ID} header — is reused rather than
     *       replaced, so one client request keeps one ID across service hops.</li>
     * </ul>
     *
     * <p>Registering the ID in the MDC on first use means a handler benefits even in a
     * service with no correlation filter installed, provided it obtains the ID before it
     * logs. MDC is thread-scoped, so the value is naturally per-request; request threads are
     * pooled, which is why filters that populate the MDC must clear it in a
     * {@code finally} block.
     *
     * @return the correlation ID in scope for this thread, never {@code null}
     */
    public static String current() {
        String existing = MDC.get(MDC_KEY);
        if (existing != null && !existing.isBlank()) {
            return existing;
        }

        String correlationId = generate();
        MDC.put(MDC_KEY, correlationId);
        return correlationId;
    }

    public static String getHeaderName() {
        return CORRELATION_ID_HEADER;
    }
}
