package ch.batbern.events.forwarding;

import ch.batbern.shared.contract.ContractCallFailedException;
import ch.batbern.shared.contract.ContractCalls;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Calls the calendar reply contract at every configured implementer through shared-kernel
 * {@link ContractCalls}. No JWT required; forwarded when there is one (ADR-016 decision 3). The
 * inbound SQS listener has none.
 *
 * <p>Never throws. 404 (the implementer has no such meeting) and 400 (a PARTSTAT it does not
 * record) are the implementer's answer to this reply and are logged as a WARN; any other failure
 * (no answer, 5xx) is logged as an ERROR and counted ({@value #FAILURES}).
 */
@Component
@Slf4j
public class HttpCalendarReplyContract implements CalendarReplyContract {

    static final String FAILURES = "forwarding.calendar_reply_contract.failures";
    static final String PATH = "/internal/calendar-replies";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(10);

    private final RestTemplate restTemplate;
    private final ContractCalls calls;
    private final Counter failures;

    @Autowired
    public HttpCalendarReplyContract(RestTemplateBuilder builder, Environment environment,
            ObjectProvider<MeterRegistry> meterRegistry) {
        this(builder.connectTimeout(CONNECT_TIMEOUT).readTimeout(READ_TIMEOUT).build(), environment,
                meterRegistry.getIfAvailable(() -> Metrics.globalRegistry));
    }

    private HttpCalendarReplyContract(RestTemplate restTemplate, Environment environment, MeterRegistry registry) {
        this(restTemplate, ContractCalls.fromEnvironment(NAME, environment, restTemplate), registry);
    }

    HttpCalendarReplyContract(RestTemplate restTemplate, List<String> implementers, MeterRegistry registry) {
        this(restTemplate, new ContractCalls(NAME, implementers, restTemplate), registry);
    }

    private HttpCalendarReplyContract(RestTemplate restTemplate, ContractCalls calls, MeterRegistry registry) {
        this.restTemplate = restTemplate;
        this.calls = calls;
        this.failures = Counter.builder(FAILURES)
                .description("Calendar replies an implementer of the calendar reply contract did not take")
                .register(registry);
    }

    /** For the S2S contract harness in tests; the contract caller owns its template. */
    RestTemplate restTemplate() {
        return restTemplate;
    }

    @Override
    public void replied(Reply reply) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("meetingId", reply.meetingId());
        body.put("attendeeEmail", reply.attendeeEmail());
        body.put("partStat", reply.partStat());
        if (calls.implementers().isEmpty()) {
            log.warn("CALENDAR_REPLY not delivered, no implementer meetingId={} partStat={}: set {}{}.implementers",
                    reply.meetingId(), reply.partStat(), ContractCalls.PROPERTY_PREFIX, NAME);
            return;
        }
        try {
            calls.post(PATH, body);
            log.info("CALENDAR_REPLY recorded meetingId={} emailPrefix={} status={}", reply.meetingId(),
                    emailPrefix(reply.attendeeEmail()), reply.partStat());
        } catch (ContractCallFailedException e) {
            int status = e.getCause() instanceof HttpStatusCodeException http ? http.getStatusCode().value() : 0;
            if (status == 404 || status == 400) {
                log.warn("CALENDAR_REPLY refused meetingId={} partStat={} answer={} ({}): discarded",
                        reply.meetingId(), reply.partStat(), status,
                        status == 404 ? "unknown meeting" : "partStat not recorded");
                return;
            }
            failed(reply, e);
        } catch (RuntimeException e) {
            // ContractRefusedException (409, which the contract does not declare) and anything else
            failed(reply, e);
        }
    }

    private void failed(Reply reply, RuntimeException e) {
        failures.increment();
        log.error("CALENDAR_REPLY not delivered meetingId={} partStat={} cause={}: discarded", reply.meetingId(),
                reply.partStat(), e.getMessage());
    }

    private static String emailPrefix(String email) {
        return email != null ? email.substring(0, Math.min(5, email.length())) : "?";
    }
}
