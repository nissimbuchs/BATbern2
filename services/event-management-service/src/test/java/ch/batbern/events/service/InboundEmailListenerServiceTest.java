package ch.batbern.events.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for InboundEmailListenerService (Story 10.17 — AC1, AC3, AC4, AC5).
 *
 * Tests MIME parsing, S3 fetch, and routing delegation.
 * TDD: RED phase — tests define behaviour before implementation.
 */
@ExtendWith(MockitoExtension.class)
class InboundEmailListenerServiceTest {

    @Mock
    private S3Client s3Client;

    @Mock
    private InboundEmailRouter router;

    private InboundEmailListenerService listenerService;

    private static final String BUCKET_NAME = "batbern-inbound-emails-staging";

    @BeforeEach
    void setUp() {
        listenerService = new InboundEmailListenerService(s3Client, router, BUCKET_NAME, new ObjectMapper());
    }

    /**
     * A minimal raw MIME email for testing.
     */
    private static final String RAW_EMAIL = "From: sender@example.com\r\n"
            + "To: replies@batbern.ch\r\n"
            + "Subject: Re: BATbern42 Registration Confirmation\r\n"
            + "MIME-Version: 1.0\r\n"
            + "Content-Type: text/plain; charset=UTF-8\r\n"
            + "\r\n"
            + "cancel\r\n";

    private String s3NotificationJson(String key) {
        return "{\"Records\":[{\"s3\":{\"bucket\":{\"name\":\"" + BUCKET_NAME
               + "\"},\"object\":{\"key\":\"" + key + "\"}}}]}";
    }

    @Test
    void handleS3Notification_withValidEmail_parsesAndRoutes() throws IOException {
        String key = "emails/test-message-id";
        byte[] rawBytes = RAW_EMAIL.getBytes(StandardCharsets.UTF_8);

        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(rawBytes)
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);

        listenerService.handleS3Notification(s3NotificationJson(key));

        ArgumentCaptor<InboundEmailRouter.ParsedEmail> captor =
                ArgumentCaptor.forClass(InboundEmailRouter.ParsedEmail.class);
        verify(router).route(captor.capture());

        InboundEmailRouter.ParsedEmail parsed = captor.getValue();
        assertThat(parsed.senderEmail()).isEqualTo("sender@example.com");
        assertThat(parsed.subject()).contains("BATbern42");
        assertThat(parsed.bodyFirstLine()).isEqualToIgnoringCase("cancel");
    }

    @Test
    void handleS3Notification_whenS3FetchFails_logsAndDiscardsGracefully() {
        String key = "emails/failing-key";
        when(s3Client.getObject(any(GetObjectRequest.class)))
                .thenThrow(S3Exception.builder().message("Not found").build());

        // Should NOT throw — exception must be caught
        listenerService.handleS3Notification(s3NotificationJson(key));

        verifyNoInteractions(router);
    }

    @Test
    void handleS3Notification_withEmptyKey_returnsEarlyWithoutRouting() {
        String notificationWithEmptyKey = s3NotificationJson("");

        listenerService.handleS3Notification(notificationWithEmptyKey);

        verifyNoInteractions(router);
        verifyNoInteractions(s3Client);
    }

    @Test
    void handleS3Notification_withInvalidJson_logsAndDiscardsGracefully() {
        // Should NOT throw for malformed JSON
        listenerService.handleS3Notification("not-json");

        verifyNoInteractions(router);
        verifyNoInteractions(s3Client);
    }

    // ─── T12.1: iCal REPLY MIME part detected and routed ──────────────────────

    @Test
    void handleS3Notification_withIcsReplyPart_callsRouteIcsReply() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\r\n"
                + "METHOD:REPLY\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:11111111-1111-1111-1111-111111111111@batbern.ch\r\n"
                + "ATTENDEE;PARTSTAT=ACCEPTED;CN=Alice:mailto:alice@partner.com\r\n"
                + "DTSTART:20260514T100000Z\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        byte[] rawBytes = buildMultipartEmail("alice@partner.com", icsBody)
                .getBytes(StandardCharsets.UTF_8);
        mockS3Return(rawBytes);

        listenerService.handleS3Notification(s3NotificationJson("emails/ics-reply-key"));

        ArgumentCaptor<InboundEmailRouter.IcsReply> captor =
                ArgumentCaptor.forClass(InboundEmailRouter.IcsReply.class);
        verify(router).routeIcsReply(captor.capture());
        verify(router, never()).route(any());

        InboundEmailRouter.IcsReply reply = captor.getValue();
        assertThat(reply.meetingUid()).isEqualTo("11111111-1111-1111-1111-111111111111@batbern.ch");
        assertThat(reply.attendeeEmail()).isEqualTo("alice@partner.com");
        assertThat(reply.partStat()).isEqualTo("ACCEPTED");
    }

    // ─── T12.2: missing ATTENDEE → WARN, router NOT called ────────────────────

    @Test
    void handleS3Notification_withIcsReplyMissingAttendee_discardsGracefully() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\r\n"
                + "METHOD:REPLY\r\n"
                + "BEGIN:VEVENT\r\n"
                + "UID:11111111-1111-1111-1111-111111111111@batbern.ch\r\n"
                + "DTSTART:20260514T100000Z\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        byte[] rawBytes = buildMultipartEmail("alice@partner.com", icsBody)
                .getBytes(StandardCharsets.UTF_8);
        mockS3Return(rawBytes);

        listenerService.handleS3Notification(s3NotificationJson("emails/ics-no-attendee"));

        verifyNoInteractions(router);
    }

    // ─── T12.3: missing UID → WARN, router NOT called ────────────────────────

    @Test
    void handleS3Notification_withIcsReplyMissingUid_discardsGracefully() throws IOException {
        String icsBody = "BEGIN:VCALENDAR\r\n"
                + "METHOD:REPLY\r\n"
                + "BEGIN:VEVENT\r\n"
                + "ATTENDEE;PARTSTAT=ACCEPTED;CN=Alice:mailto:alice@partner.com\r\n"
                + "DTSTART:20260514T100000Z\r\n"
                + "END:VEVENT\r\n"
                + "END:VCALENDAR\r\n";

        byte[] rawBytes = buildMultipartEmail("alice@partner.com", icsBody)
                .getBytes(StandardCharsets.UTF_8);
        mockS3Return(rawBytes);

        listenerService.handleS3Notification(s3NotificationJson("emails/ics-no-uid"));

        verifyNoInteractions(router);
    }

    // ─── T12.4: plain-text email → existing router.route() path unchanged ─────

    @Test
    void handleS3Notification_withPlainTextEmail_usesExistingRoutePath() throws IOException {
        byte[] rawBytes = RAW_EMAIL.getBytes(StandardCharsets.UTF_8);
        mockS3Return(rawBytes);

        listenerService.handleS3Notification(s3NotificationJson("emails/plain-text-key"));

        verify(router).route(any());
        verify(router, never()).routeIcsReply(any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void mockS3Return(byte[] rawBytes) {
        ResponseInputStream<GetObjectResponse> responseStream = new ResponseInputStream<>(
                GetObjectResponse.builder().build(),
                new ByteArrayInputStream(rawBytes)
        );
        when(s3Client.getObject(any(GetObjectRequest.class))).thenReturn(responseStream);
    }

    private String buildMultipartEmail(String from, String icsBody) {
        String boundary = "boundary-ics-reply-test";
        return "From: " + from + "\r\n"
                + "To: replies@batbern.ch\r\n"
                + "Subject: Accepted: Partner Meeting\r\n"
                + "MIME-Version: 1.0\r\n"
                + "Content-Type: multipart/mixed; boundary=\"" + boundary + "\"\r\n"
                + "\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/plain; charset=UTF-8\r\n"
                + "\r\n"
                + "Accepted.\r\n"
                + "--" + boundary + "\r\n"
                + "Content-Type: text/calendar; charset=UTF-8; method=REPLY\r\n"
                + "\r\n"
                + icsBody
                + "--" + boundary + "--\r\n";
    }
}
