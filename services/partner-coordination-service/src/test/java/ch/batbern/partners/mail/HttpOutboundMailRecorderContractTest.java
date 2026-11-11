package ch.batbern.partners.mail;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.test.contract.ContractServer;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Story 16.9 AC1/AC2: partner-coordination records its meeting mails in EMS through
 * {@code POST /internal/mails}, best effort. Contract test (Story 17.0b2): every request is validated
 * against {@code docs/api/internal/ems-internal.openapi.yml}. No real mail and no real EMS.
 */
@DisplayName("HttpOutboundMailRecorder (Story 16.9)")
class HttpOutboundMailRecorderContractTest {

    private static final String BASE = "http://event-management:8080";
    private static final String TO = "success+p1@simulator.amazonses.com,success+p2@simulator.amazonses.com";

    private static final String EMS_INTERNAL = "docs/api/internal/ems-internal.openapi.yml";
    private static final String MAILS = "/internal/mails";

    private ContractServer server;
    private HttpOutboundMailRecorder recorder;
    private MailContext context;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(HttpOutboundMailRecorder.class)).addAppender(logs);
        RestTemplate restTemplate = new RestTemplate();
        server = ContractServer.bindTo(restTemplate, EMS_INTERNAL);
        recorder = new HttpOutboundMailRecorder(restTemplate, BASE);
        context = MailContext.of(MailTrigger.organizer("organizer1"), "BATbern60", MailContext.Audience.PARTNERS,
                "partner-meeting-invite").withReference("partner-meeting:6f1c");
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(HttpOutboundMailRecorder.class)).detachAppender(logs);
        SecurityContextHolder.clearContext();
        server.close();
    }

    @Test
    @DisplayName("AC1: posts the flat payload to /internal/mails; on the async send thread (no user) without Authorization")
    void record_postsPayloadWithoutToken() {
        server.expect(POST, MAILS).andExpect(request -> {
            assertThat(request.getURI().toString()).isEqualTo(BASE + MAILS);
            assertThat(request.getHeaders().containsHeader(HttpHeaders.AUTHORIZATION)).isFalse();
            assertThat(request.getHeaders().getContentType()).isNotNull()
                    .matches(t -> t.isCompatibleWith(MediaType.APPLICATION_JSON));
            DocumentContext json = JsonPath.parse(((MockClientHttpRequest) request).getBodyAsString());
            assertThat(json.read("$.sendGroupId", String.class)).isEqualTo(context.sendGroupId());
            assertThat(json.read("$.eventCode", String.class)).isEqualTo("BATbern60");
            assertThat(json.read("$.audience", String.class)).isEqualTo("PARTNERS");
            assertThat(json.read("$.templateKey", String.class)).isEqualTo("partner-meeting-invite");
            assertThat(json.read("$.contentSource", String.class)).isEqualTo("TEMPLATE");
            assertThat(json.read("$.senderUsername", String.class)).isEqualTo("organizer1");
            assertThat(json.read("$.test", Boolean.class)).isFalse();
            assertThat(json.read("$.reference", String.class)).isEqualTo("partner-meeting:6f1c");
            assertThat(json.read("$.to", String.class)).isEqualTo(TO);
            assertThat(json.read("$.cc", List.class)).isEmpty();
            assertThat(json.read("$.subject", String.class)).isEqualTo("Invite");
            assertThat(json.read("$.htmlBody", String.class)).isEqualTo("<p>i</p>");
            assertThat(json.read("$.sesMessageId", String.class)).isEqualTo("ses-1");
            assertThat(json.read("$", Map.class)).doesNotContainKey("secrets");
        }).respondWithStatus(HttpStatus.ACCEPTED);

        recorder.record(context, new OutboundMail(TO, List.of(), "Invite", "<p>i</p>", "ses-1", null));

        server.verify();
    }

    @Test
    @DisplayName("sent while a user is signed in: the user's JWT is forwarded (S2sHeaders, ADR-016 decision 3)")
    void record_forwardsUsersJwt_when_signedIn() {
        Jwt jwt = Jwt.withTokenValue("organizer-token").header("alg", "RS256").claim("sub", "organizer1")
                .expiresAt(Instant.now().plusSeconds(3600)).build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
        server.expect(POST, MAILS)
                .andExpect(request -> assertThat(request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                        .isEqualTo("Bearer organizer-token"))
                .respondWithStatus(HttpStatus.ACCEPTED);

        recorder.record(context, mail());

        server.verify();
        assertThat(logs.list).isEmpty();
    }

    @Test
    @DisplayName("AC2: EMS answering 503 is logged once as WARN with the group, never thrown")
    void record_serverError_warnsOnly() {
        // 503 is not an EMS answer the spec declares (an ALB/Service Connect one): custom creator,
        // the request is still validated.
        server.expect(POST, MAILS).respondWith(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("503");
    }

    @Test
    @DisplayName("AC2: a timeout is logged once as WARN, never thrown")
    void record_timeout_warnsOnly() {
        server.expect(POST, MAILS).respondWith(withException(new SocketTimeoutException("Read timed out")));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("ResourceAccessException");
    }

    @Test
    @DisplayName("AC2: connection refused is logged once as WARN, never thrown")
    void record_connectionRefused_warnsOnly() {
        server.expect(POST, MAILS).respondWith(withException(new ConnectException("Connection refused")));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("ResourceAccessException");
    }

    @Test
    @DisplayName("EMS refusing the record (400, e.g. more than 500 addresses) is logged once as WARN, never thrown")
    void record_refused_warnsOnly() {
        server.expect(POST, MAILS).respondWithStatus(HttpStatus.BAD_REQUEST);

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("400");
    }

    private static OutboundMail mail() {
        return new OutboundMail(TO, List.of(), "Invite", "<p>i</p>", "ses-1", null);
    }

    private void assertWarnedOnce(String cause) {
        assertThat(logs.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage()).contains("partner-meeting:6f1c").contains(cause)
                    .doesNotContain("simulator.amazonses.com").doesNotContain("<p>i</p>");
        });
    }
}
