package ch.batbern.partners.mail;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.mail.OutboundMail;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

/**
 * Story 16.9 AC1/AC2: partner-coordination records its meeting mails in EMS through
 * {@code POST /internal/mails}, best effort. No real mail and no real EMS: a mock server only.
 */
@DisplayName("HttpOutboundMailRecorder (Story 16.9)")
class HttpOutboundMailRecorderTest {

    private static final String BASE = "http://event-management:8080";
    private static final String TO = "success+p1@simulator.amazonses.com,success+p2@simulator.amazonses.com";

    private MockRestServiceServer server;
    private HttpOutboundMailRecorder recorder;
    private MailContext context;
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @BeforeEach
    void setUp() {
        logs.start();
        ((Logger) LoggerFactory.getLogger(HttpOutboundMailRecorder.class)).addAppender(logs);
        RestTemplate restTemplate = new RestTemplate();
        server = MockRestServiceServer.bindTo(restTemplate).build();
        recorder = new HttpOutboundMailRecorder(restTemplate, BASE);
        context = MailContext.of(MailTrigger.organizer("organizer1"), "BATbern60", MailContext.Audience.PARTNERS,
                "partner-meeting-invite").withReference("partner-meeting:6f1c");
    }

    @AfterEach
    void tearDown() {
        ((Logger) LoggerFactory.getLogger(HttpOutboundMailRecorder.class)).detachAppender(logs);
    }

    @Test
    @DisplayName("AC1: posts the flat payload to /internal/mails without an Authorization header")
    void record_postsPayloadWithoutToken() {
        server.expect(requestTo(BASE + "/internal/mails"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(headerDoesNotExist("Authorization"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sendGroupId").value(context.sendGroupId()))
                .andExpect(jsonPath("$.eventCode").value("BATbern60"))
                .andExpect(jsonPath("$.audience").value("PARTNERS"))
                .andExpect(jsonPath("$.templateKey").value("partner-meeting-invite"))
                .andExpect(jsonPath("$.contentSource").value("TEMPLATE"))
                .andExpect(jsonPath("$.senderUsername").value("organizer1"))
                .andExpect(jsonPath("$.test").value(false))
                .andExpect(jsonPath("$.reference").value("partner-meeting:6f1c"))
                .andExpect(jsonPath("$.to").value(TO))
                .andExpect(jsonPath("$.cc").isEmpty())
                .andExpect(jsonPath("$.subject").value("Invite"))
                .andExpect(jsonPath("$.htmlBody").value("<p>i</p>"))
                .andExpect(jsonPath("$.sesMessageId").value("ses-1"))
                .andExpect(jsonPath("$.secrets").doesNotExist())
                .andRespond(withStatus(HttpStatus.ACCEPTED));

        recorder.record(context, new OutboundMail(TO, List.of(), "Invite", "<p>i</p>", "ses-1", null));

        server.verify();
    }

    @Test
    @DisplayName("AC2: EMS answering 503 is logged once as WARN with the group, never thrown")
    void record_serverError_warnsOnly() {
        server.expect(requestTo(BASE + "/internal/mails")).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("503");
    }

    @Test
    @DisplayName("AC2: a timeout is logged once as WARN, never thrown")
    void record_timeout_warnsOnly() {
        server.expect(requestTo(BASE + "/internal/mails"))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("ResourceAccessException");
    }

    @Test
    @DisplayName("AC2: connection refused is logged once as WARN, never thrown")
    void record_connectionRefused_warnsOnly() {
        server.expect(requestTo(BASE + "/internal/mails"))
                .andRespond(withException(new ConnectException("Connection refused")));

        assertThatCode(() -> recorder.record(context, mail())).doesNotThrowAnyException();

        assertWarnedOnce("ResourceAccessException");
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
