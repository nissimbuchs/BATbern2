package ch.batbern.events.forwarding;

import ch.batbern.events.forwarding.CalendarReplyContract.Reply;
import ch.batbern.shared.test.contract.ContractServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.boot.restclient.RestTemplateBuilder;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

/**
 * Contract test of {@link HttpCalendarReplyContract} against the calendar reply contract EMS owns
 * ({@code docs/api/internal/ems-internal.openapi.yml}, Epic 17 entry 8b): every request is
 * validated against the spec, at the configured implementer. The inbound listener has no user, so
 * no JWT goes out. Nothing the implementer answers makes the call throw.
 */
@DisplayName("HttpCalendarReplyContract honours the calendar reply contract")
class HttpCalendarReplyContractTest {

    static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final UUID MEETING = UUID.fromString("0b6c1f7e-2c1d-4c55-9a43-2d6f4e8a9b10");
    private static final String IMPLEMENTER = "http://implementer:8080";

    private ContractServer implementer;
    private SimpleMeterRegistry meters;
    private HttpCalendarReplyContract contract;

    @BeforeEach
    void setUp() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        implementer = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        meters = new SimpleMeterRegistry();
        contract = new HttpCalendarReplyContract(restTemplate, List.of(IMPLEMENTER), meters);
    }

    @AfterEach
    void tearDown() {
        implementer.close();
    }

    private double failures() {
        return meters.counter(HttpCalendarReplyContract.FAILURES).count();
    }

    @Test
    void should_postTheReply_toTheConfiguredImplementer_withoutJwt() {
        implementer.expect(POST, HttpCalendarReplyContract.PATH)
                .andExpect(r -> {
                    assertThat(r.getURI().toString()).isEqualTo(IMPLEMENTER + "/internal/calendar-replies");
                    assertThat(r.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
                    assertThat(((MockClientHttpRequest) r).getBodyAsString())
                            .contains("\"meetingId\":\"" + MEETING + "\"", "\"attendeeEmail\":\"anna.meier@example.com\"",
                                    "\"partStat\":\"ACCEPTED\"");
                })
                .respondWithStatus(HttpStatus.NO_CONTENT);

        contract.replied(new Reply(MEETING, "anna.meier@example.com", "ACCEPTED"));

        implementer.verify();
        assertThat(failures()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"NOT_FOUND", "BAD_REQUEST"})
    @DisplayName("unknown meeting (404) or PARTSTAT (400): discarded with a WARN, not counted")
    void should_discardTheReply_when_theImplementerRefusesIt(HttpStatus status) {
        implementer.expect(POST, HttpCalendarReplyContract.PATH).respondWithExample(status);

        assertThatCode(() -> contract.replied(new Reply(MEETING, "anna.meier@example.com", "NEEDS-ACTION")))
                .doesNotThrowAnyException();

        implementer.verify();
        assertThat(failures()).isZero();
    }

    @Test
    @DisplayName("implementer down: no exception, counted")
    void should_countTheFailure_when_theImplementerDoesNotAnswer() {
        implementer.expect(POST, HttpCalendarReplyContract.PATH).respondWith(request -> {
            throw new ConnectException("Connection refused");
        });

        assertThatCode(() -> contract.replied(new Reply(MEETING, "anna.meier@example.com", "DECLINED")))
                .doesNotThrowAnyException();

        implementer.verify();
        assertThat(failures()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("no implementer configured: nothing sent, no exception, not counted")
    void should_sendNothing_when_noImplementerIsConfigured() {
        RestTemplate restTemplate = new RestTemplateBuilder().build();
        try (ContractServer none = ContractServer.bindTo(restTemplate, EMS_INTERNAL)) {
            SimpleMeterRegistry registry = new SimpleMeterRegistry();
            HttpCalendarReplyContract unconfigured = new HttpCalendarReplyContract(restTemplate, List.of(), registry);

            assertThatCode(() -> unconfigured.replied(new Reply(MEETING, "anna.meier@example.com", "ACCEPTED")))
                    .doesNotThrowAnyException();

            none.verify();
            assertThat(registry.counter(HttpCalendarReplyContract.FAILURES).count()).isZero();
        }
    }

    @Test
    @DisplayName("implementer answers 5xx (not a contract status): no exception, counted")
    void should_countTheFailure_when_theImplementerFails() {
        implementer.expect(POST, HttpCalendarReplyContract.PATH).respondWith(withServerError());

        assertThatCode(() -> contract.replied(new Reply(MEETING, "anna.meier@example.com", "TENTATIVE")))
                .doesNotThrowAnyException();

        implementer.verify();
        assertThat(failures()).isEqualTo(1.0);
    }
}
