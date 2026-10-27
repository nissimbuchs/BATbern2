package ch.batbern.events.forwarding;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.EmailForwardingOutcome;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.exception.UserServiceException;
import ch.batbern.events.forwarding.EmailForwardingService.Result;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.events.service.DistributionListService;
import ch.batbern.shared.service.EmailService;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Forwarding end to end with real alias/authorization/resolution/rewrite logic and mocked edges
 * (CUMS, distribution lists, SES, idempotency table). Ports T12 of the Lambda's
 * email-forwarder.test.ts and adds the EMS-only guarantees.
 *
 * <p>Addresses use the SES mailbox simulator domain: RFC 2606 domains are filtered out as
 * reserved before sending, and nothing here reaches SES anyway.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EmailForwardingService")
class EmailForwardingServiceTest {

    private static final String KEY = "forwarding/abc123";
    private static final String SIM = "@simulator.amazonses.com";
    private static final String ORGANIZER = "success+alice" + SIM;
    private static final String ORGANIZER_2 = "success+bob" + SIM;
    private static final String STRANGER = "success+stranger" + SIM;
    private static final String DMARC_PASS = "amazonses.com; spf=pass; dkim=pass header.i=@simulator.amazonses.com;"
            + " dmarc=pass header.from=simulator.amazonses.com;";
    private static final String DMARC_FAIL = "amazonses.com; spf=pass; dkim=none; dmarc=fail;";

    @Mock
    private UserApiClient userApiClient;
    @Mock
    private AdminSettingsService adminSettingsService;
    @Mock
    private DistributionListService distributionListService;
    @Mock
    private EmailForwardingLogService forwardingLog;
    @Mock
    private ForwardingMetrics metrics;
    @Mock
    private EmailService emailService;

    private EmailForwardingService service;

    @BeforeEach
    void setUp() {
        ForwardingDirectory directory = new ForwardingDirectory(userApiClient);
        EmailForwardingProperties properties =
                new EmailForwardingProperties(true, "q", "batbern.ch", null, "batbern-staging-forwarder", 0);
        service = new EmailForwardingService(properties, new ForwardingSenderAuthorizer(directory),
                new ForwardingRecipientResolver(directory, adminSettingsService, distributionListService),
                new ForwardedMessageRewriter(), forwardingLog, metrics, emailService);

        when(forwardingLog.claim(anyString())).thenReturn(true);
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("alice", "bob"));
        when(userApiClient.getUserByUsername("alice")).thenReturn(new UserResponse().id("alice").email(ORGANIZER));
        when(userApiClient.getUserByUsername("bob")).thenReturn(new UserResponse().id("bob").email(ORGANIZER_2));
        when(distributionListService.resolveSpeakers("BATbern60"))
                .thenReturn(set("success+s1" + SIM, "success+s2" + SIM));
        when(distributionListService.resolveModerator("BATbern60")).thenReturn(set(ORGANIZER));
        when(distributionListService.resolveParticipants("BATbern60"))
                .thenReturn(set("success+p1" + SIM, "success+p2" + SIM, "success+p3" + SIM));
    }

    // ---- delivery modes ----

    @Test
    @DisplayName("should_sendOnePrivateCopyPerOrganizer_when_anyoneWritesToInfo")
    void should_sendOnePrivateCopyPerOrganizer_when_anyoneWritesToInfo() throws Exception {
        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.FORWARDED);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(emailService).sendRawMessage(body.capture(), eq(List.of(ORGANIZER)), eq("batbern-staging-forwarder"));
        verify(emailService).sendRawMessage(any(), eq(List.of(ORGANIZER_2)), eq("batbern-staging-forwarder"));
        MimeMessage sent = parse(body.getValue());
        assertThat(sent.getHeader("From", null)).contains("via BATbern").contains("noreply@batbern.ch");
        assertThat(sent.getHeader("Reply-To", null)).isEqualTo(STRANGER);
        assertThat(sent.getHeader("To", null)).isEqualTo("info@batbern.ch");
        verify(forwardingLog).complete(KEY, EmailForwardingOutcome.FORWARDED, "info", 2, 0);
        verify(metrics).increment(ForwardingMetrics.FORWARDED);
    }

    @Test
    @DisplayName("should_sendOneVisibleMailWithModeratorInCc_when_organizerWritesToSpeakerAlias")
    void should_sendOneVisibleMailWithModeratorInCc_when_organizerWritesToSpeakerAlias() throws Exception {
        Result result = service.forward(KEY, mail(ORGANIZER_2, "batbern60-speaker@batbern.ch", null, DMARC_PASS, null));

        assertThat(result).isEqualTo(Result.FORWARDED);
        ArgumentCaptor<byte[]> body = ArgumentCaptor.forClass(byte[].class);
        verify(emailService, times(1)).sendRawMessage(body.capture(),
                eq(List.of("success+s1" + SIM, "success+s2" + SIM, ORGANIZER)), eq("batbern-staging-forwarder"));
        MimeMessage sent = parse(body.getValue());
        assertThat(sent.getHeader("To", null)).isEqualTo("success+s1" + SIM + ", success+s2" + SIM);
        assertThat(sent.getHeader("Cc", null)).isEqualTo(ORGANIZER);
    }

    @Test
    @DisplayName("should_notDoubleDeliverModerator_when_speakerAndModeratorAliasesBothAddressed")
    void should_notDoubleDeliverModerator_when_speakerAndModeratorAliasesBothAddressed() {
        service.forward(KEY, mail(ORGANIZER_2, "batbern60-speaker@batbern.ch", "batbern60-moderator@batbern.ch",
                DMARC_PASS, null));

        // The moderator is folded into To with the extras of a mixed mail, so it is not Cc'd again.
        verify(emailService, times(1)).sendRawMessage(any(),
                eq(List.of("success+s1" + SIM, "success+s2" + SIM, ORGANIZER)), anyString());
    }

    @Test
    @DisplayName("should_deliverToModerator_when_anyoneWritesToStandaloneModeratorAlias")
    void should_deliverToModerator_when_anyoneWritesToStandaloneModeratorAlias() {
        Result result = service.forward(KEY, mail(STRANGER, "batbern60-moderator@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.FORWARDED);
        verify(emailService).sendRawMessage(any(), eq(List.of(ORGANIZER)), anyString());
    }

    @Test
    @DisplayName("should_sendToEveryParticipantPrivately_when_authenticatedOrganizerWritesToParticipants")
    void should_sendToEveryParticipantPrivately_when_authenticatedOrganizerWritesToParticipants() {
        Result result = service.forward(KEY, mail(ORGANIZER, "batbern60-participants@batbern.ch", null,
                DMARC_PASS, null));

        assertThat(result).isEqualTo(Result.FORWARDED);
        verify(emailService, times(3)).sendRawMessage(any(), anyList(), anyString());
        verify(forwardingLog).complete(KEY, EmailForwardingOutcome.FORWARDED, "batbern60-participants", 3, 0);
    }

    // ---- authorization ----

    @Test
    @DisplayName("should_reject_when_nonOrganizerWritesToParticipants")
    void should_reject_when_nonOrganizerWritesToParticipants() {
        Result result = service.forward(KEY, mail(STRANGER, "batbern60-participants@batbern.ch", null,
                DMARC_PASS, null));

        assertThat(result).isEqualTo(Result.REJECTED);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
        verify(metrics).increment(ForwardingMetrics.REJECTED);
        verify(forwardingLog).complete(KEY, EmailForwardingOutcome.REJECTED, "batbern60-participants", 0, 0);
    }

    @Test
    @DisplayName("should_reject_when_organizerAddressIsSpoofed")
    void should_reject_when_organizerAddressIsSpoofed() {
        Result result = service.forward(KEY, mail(ORGANIZER, "batbern60-participants@batbern.ch", null,
                DMARC_FAIL, null));

        assertThat(result).isEqualTo(Result.REJECTED);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_forwardOnlyAuthorizedAliases_when_mixedMail")
    void should_forwardOnlyAuthorizedAliases_when_mixedMail() {
        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", "batbern60-participants@batbern.ch",
                DMARC_PASS, null));

        assertThat(result).isEqualTo(Result.FORWARDED);
        verify(emailService, times(2)).sendRawMessage(any(), anyList(), anyString());
        verify(distributionListService, never()).resolveParticipants(anyString());
    }

    // ---- drops (no claim, no rejection metric) ----

    @Test
    @DisplayName("should_dropSilently_when_mailToNoReply")
    void should_dropSilently_when_mailToNoReply() {
        Result result = service.forward(KEY, mail(STRANGER, "noreply@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.DROPPED_NO_REPLY);
        verify(forwardingLog, never()).claim(anyString());
        verify(metrics, never()).increment(ForwardingMetrics.REJECTED);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_drop_when_outOfOfficeReplyToInfo")
    void should_drop_when_outOfOfficeReplyToInfo() {
        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null,
                "Auto-Submitted: auto-replied\r\n"));

        assertThat(result).isEqualTo(Result.DROPPED_AUTO_REPLY);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_drop_when_iMipCalendarReply")
    void should_drop_when_iMipCalendarReply() {
        String raw = "From: " + STRANGER + "\r\nTo: events@batbern.ch\r\nSubject: Accepted\r\n"
                + "Content-Type: text/calendar; method=REPLY; charset=UTF-8\r\n\r\nBEGIN:VCALENDAR\r\n";

        Result result = service.forward(KEY, raw.getBytes(StandardCharsets.UTF_8));

        assertThat(result).isEqualTo(Result.DROPPED_CALENDAR_REPLY);
        verify(metrics).increment(ForwardingMetrics.CALENDAR_REPLIES_DROPPED);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_drop_when_mailCarriesOurForwardedMarker")
    void should_drop_when_mailCarriesOurForwardedMarker() {
        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null,
                MailClassifier.FORWARDED_MARKER_HEADER + ": 1\r\n"));

        assertThat(result).isEqualTo(Result.DROPPED_LOOP);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_drop_when_senderIsInForwardingDomain")
    void should_drop_when_senderIsInForwardingDomain() {
        Result result = service.forward(KEY, mail("noreply@batbern.ch", "info@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.DROPPED_LOOP);
    }

    @Test
    @DisplayName("should_countUnresolved_when_onlyUnknownAlias")
    void should_countUnresolved_when_onlyUnknownAlias() {
        Result result = service.forward(KEY, mail(STRANGER, "whoever@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.UNRESOLVED);
        verify(metrics).increment(ForwardingMetrics.UNRESOLVED);
        verify(forwardingLog, never()).claim(anyString());
    }

    // ---- idempotency and failures ----

    @Test
    @DisplayName("should_skip_when_s3KeyAlreadyClaimed")
    void should_skip_when_s3KeyAlreadyClaimed() {
        when(forwardingLog.claim(KEY)).thenReturn(false);

        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.DUPLICATE);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
        verify(metrics).increment(ForwardingMetrics.DUPLICATES_SKIPPED);
    }

    @Test
    @DisplayName("should_markSendingBeforeFirstCopy_when_forwarding")
    void should_markSendingBeforeFirstCopy_when_forwarding() {
        service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(forwardingLog, emailService);
        order.verify(forwardingLog).markSending(KEY);
        order.verify(emailService, org.mockito.Mockito.atLeastOnce()).sendRawMessage(any(), anyList(), anyString());
    }

    @Test
    @DisplayName("should_alarm_when_duplicateIsAClaimStuckAfterSending")
    void should_alarm_when_duplicateIsAClaimStuckAfterSending() {
        when(forwardingLog.claim(KEY)).thenReturn(false);
        when(forwardingLog.isStuckAfterSending(KEY)).thenReturn(true);

        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.DUPLICATE);
        verify(metrics).increment(ForwardingMetrics.FAILED);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_drop_when_mailHasMoreThanOneFromHeader")
    void should_drop_when_mailHasMoreThanOneFromHeader() {
        Result result = service.forward(KEY, mail(STRANGER, "batbern60-participants@batbern.ch", null, DMARC_PASS,
                "From: \"Alice\" <" + ORGANIZER + ">\r\n"));

        assertThat(result).isEqualTo(Result.DROPPED_MALFORMED);
        verify(forwardingLog, never()).claim(anyString());
    }

    @Test
    @DisplayName("should_releaseClaimAndRethrow_when_rewriteFailsBeforeAnySend")
    void should_releaseClaimAndRethrow_when_rewriteFailsBeforeAnySend() {
        // Review of #1137: a failure between claim and markSending must give the claim back.
        // Otherwise the redelivery (after 300 s) still sees a live claim (< 360 s) and the mail
        // ends as a silent DUPLICATE.
        ForwardedMessageRewriter failingRewriter = org.mockito.Mockito.mock(ForwardedMessageRewriter.class);
        when(failingRewriter.rewrite(any(), any(), any(), any(), any(), any()))
                .thenThrow(new IllegalStateException("Could not rewrite inbound mail for forwarding"));
        ForwardingDirectory directory = new ForwardingDirectory(userApiClient);
        EmailForwardingService withFailingRewriter = new EmailForwardingService(
                new EmailForwardingProperties(true, "q", "batbern.ch", null, "cs", 0),
                new ForwardingSenderAuthorizer(directory),
                new ForwardingRecipientResolver(directory, adminSettingsService, distributionListService),
                failingRewriter, forwardingLog, metrics, emailService);

        assertThatThrownBy(() -> withFailingRewriter.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null)))
                .isInstanceOf(IllegalStateException.class);
        verify(forwardingLog).release(KEY);
        verify(forwardingLog, never()).markSending(anyString());
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_releaseClaimAndRethrow_when_cumsFailsBeforeAnySend")
    void should_releaseClaimAndRethrow_when_cumsFailsBeforeAnySend() {
        when(userApiClient.getOrganizerUsernames()).thenThrow(new UserServiceException("CUMS down"));

        assertThatThrownBy(() -> service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null)))
                .isInstanceOf(UserServiceException.class);
        verify(forwardingLog).release(KEY);
        verify(emailService, never()).sendRawMessage(any(), anyList(), any());
    }

    @Test
    @DisplayName("should_releaseClaimAndRethrow_when_cumsFailsDuringAuthorization")
    void should_releaseClaimAndRethrow_when_cumsFailsDuringAuthorization() {
        when(userApiClient.getOrganizerUsernames()).thenThrow(new UserServiceException("CUMS down"));

        assertThatThrownBy(() -> service.forward(KEY, mail(ORGANIZER, "batbern60-participants@batbern.ch", null,
                DMARC_PASS, null)))
                .isInstanceOf(UserServiceException.class);
        verify(forwardingLog).release(KEY);
        verify(forwardingLog, never()).complete(anyString(), any(), anyString(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("should_recordPartial_when_oneCopyFails")
    void should_recordPartial_when_oneCopyFails() {
        doThrow(new RuntimeException("SES throttled"))
                .when(emailService).sendRawMessage(any(), eq(List.of(ORGANIZER_2)), anyString());

        Result result = service.forward(KEY, mail(STRANGER, "info@batbern.ch", null, null, null));

        assertThat(result).isEqualTo(Result.PARTIAL);
        verify(forwardingLog).complete(KEY, EmailForwardingOutcome.PARTIAL, "info", 1, 1);
        verify(metrics).increment(ForwardingMetrics.FAILED);
    }

    @Test
    @DisplayName("should_skipReservedDomainRecipients_when_resolved")
    void should_skipReservedDomainRecipients_when_resolved() {
        when(distributionListService.resolveParticipants("BATbern60"))
                .thenReturn(set("fixture@example.com", "success+p1" + SIM));

        service.forward(KEY, mail(ORGANIZER, "batbern60-participants@batbern.ch", null, DMARC_PASS, null));

        verify(emailService, times(1)).sendRawMessage(any(), eq(List.of("success+p1" + SIM)), anyString());
        verify(forwardingLog).complete(eq(KEY), eq(EmailForwardingOutcome.FORWARDED), anyString(), eq(1), anyInt());
    }

    // ---- helpers ----

    private static byte[] mail(String from, String to, String cc, String authenticationResults, String extraHeaders) {
        StringBuilder raw = new StringBuilder();
        if (authenticationResults != null) {
            raw.append("Authentication-Results: ").append(authenticationResults).append("\r\n");
        }
        raw.append("From: \"Test Sender\" <").append(from).append(">\r\n");
        raw.append("To: ").append(to).append("\r\n");
        if (cc != null) {
            raw.append("Cc: ").append(cc).append("\r\n");
        }
        if (extraHeaders != null) {
            raw.append(extraHeaders);
        }
        raw.append("Subject: Hallo\r\nMessage-ID: <m1@simulator.amazonses.com>\r\n")
                .append("Content-Type: text/plain; charset=UTF-8\r\n\r\nHallo zusammen\r\n");
        return raw.toString().getBytes(StandardCharsets.UTF_8);
    }

    private static MimeMessage parse(byte[] raw) throws Exception {
        return new MimeMessage(Session.getInstance(new Properties()), new ByteArrayInputStream(raw));
    }

    private static Set<String> set(String... values) {
        return new LinkedHashSet<>(List.of(values));
    }
}
