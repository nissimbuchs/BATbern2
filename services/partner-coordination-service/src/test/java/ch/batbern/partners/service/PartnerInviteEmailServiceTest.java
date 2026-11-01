package ch.batbern.partners.service;

import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for PartnerInviteEmailService — branded, bilingual (DE + EN) calendar
 * invite + cancellation emails matching the batbern-default look (inline-styled, since
 * the partner service has no shared layout engine).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PartnerInviteEmailService Unit Tests")
class PartnerInviteEmailServiceTest {

    @Mock
    private EmailService emailService;

    private PartnerInviteEmailService service;

    @BeforeEach
    void setUp() {
        service = new PartnerInviteEmailService(emailService);
        ReflectionTestUtils.setField(service, "organizerName", "BATbern Organisationsteam");
        ReflectionTestUtils.setField(service, "baseUrl", "https://batbern.ch");
    }

    private static final UUID MEETING_ID = UUID.fromString("6f1c0000-0000-4000-8000-000000000001");

    private ArgumentCaptor<MailContext> context;

    private String captureSentHtml(String expectedSubjectPrefix) {
        context = ArgumentCaptor.forClass(MailContext.class);
        ArgumentCaptor<String> subject = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> html = ArgumentCaptor.forClass(String.class);
        // One mail, every recipient as its own To: address (a comma-joined string broke in #1104).
        verify(emailService).sendGroupHtmlEmailWithAttachments(context.capture(),
                eq(List.of("partner@example.com", "organizer@batbern.ch")), eq(List.of()),
                subject.capture(), html.capture(), anyList());
        assertThat(subject.getValue()).startsWith(expectedSubjectPrefix);
        return html.getValue();
    }

    @Test
    @DisplayName("invite email is branded, bilingual, and carries the meeting details")
    void should_buildBrandedBilingualInvite() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "KI in der Software-Architektur", "BATbern61",
                LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), "Welle7, Bern");

        service.sendCalendarInvites(
                List.of("partner@example.com", "organizer@batbern.ch"), details, new byte[]{1, 2, 3},
                MEETING_ID, "organizer1");

        String html = captureSentHtml("Einladung / Invitation:");
        // Branding (batbern-default look)
        assertThat(html).contains("https://batbern.ch/BATbern_white_logo.png");
        assertThat(html).contains("background-color:#1976d2");
        // Bilingual content
        assertThat(html).contains("Sehr geehrte Damen und Herren");
        assertThat(html).contains("Dear Sir or Madam");
        // Detail card — both label languages + values
        assertThat(html).contains("Datum").contains("Date");
        assertThat(html).contains("03.09.2026");
        assertThat(html).contains("16:00 – 17:00");
        assertThat(html).contains("Welle7, Bern");
        assertThat(html).contains("KI in der Software-Architektur");
        // No inline style attributes leaked as literal placeholders, footer present
        assertThat(html).contains("All rights reserved");
    }

    @Test
    @DisplayName("invite omits the location row when no location is given")
    void should_omitLocationRow_whenLocationBlank() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "Event", "BATbern61",
                LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), "  ");

        service.sendCalendarInvites(
                List.of("partner@example.com", "organizer@batbern.ch"), details, new byte[]{1},
                MEETING_ID, "organizer1");

        String html = captureSentHtml("Einladung / Invitation:");
        assertThat(html).doesNotContain(">Ort<").doesNotContain(">Location<");
    }

    @Test
    @DisplayName("cancellation email is branded and bilingual")
    void should_buildBrandedBilingualCancellation() {
        service.sendCancellationNotice(
                List.of("partner@example.com", "organizer@batbern.ch"),
                "KI in der Software-Architektur", "BATbern61",
                LocalDate.of(2026, 9, 3), new byte[]{9}, MEETING_ID, "organizer1");

        String html = captureSentHtml("Absage / Cancellation:");
        assertThat(html).contains("background-color:#1976d2");
        assertThat(html).contains("wurde abgesagt");
        assertThat(html).contains("has been cancelled");
        assertThat(html).contains("03.09.2026");
    }

    @Test
    @DisplayName("event title is HTML-escaped to prevent markup injection")
    void should_escapeEventTitle() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "Title <script>alert(1)</script>", "BATbern61",
                LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), null);

        service.sendCalendarInvites(
                List.of("partner@example.com", "organizer@batbern.ch"), details, new byte[]{1},
                MEETING_ID, "organizer1");

        String html = captureSentHtml("Einladung / Invitation:");
        assertThat(html).contains("&lt;script&gt;");
        assertThat(html).doesNotContain("<script>alert(1)</script>");
    }

    @Test
    @DisplayName("does not send when there are no recipients")
    void should_notSend_whenNoRecipients() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "Event", "BATbern61",
                LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), "Bern");

        service.sendCalendarInvites(List.of(), details, new byte[]{1}, MEETING_ID, "organizer1");

        verify(emailService, org.mockito.Mockito.never())
                .sendGroupHtmlEmailWithAttachments(any(MailContext.class), anyList(), anyList(), any(), any(),
                        anyList());
    }

    @Test
    @DisplayName("a failed invite send reaches the caller instead of being swallowed")
    void should_propagateFailure_when_inviteSendFails() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "Event", "BATbern61",
                LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), "Bern");
        org.mockito.Mockito.doThrow(new IllegalArgumentException("none deliverable"))
                .when(emailService).sendGroupHtmlEmailWithAttachments(any(MailContext.class), anyList(), anyList(),
                        any(), any(), anyList());

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.sendCalendarInvites(
                        List.of("partner@example.com"), details, new byte[]{1}, MEETING_ID, "organizer1"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("16.9 AC1/AC3: the invite carries a PARTNERS context with sender, event and meeting reference")
    void should_passInviteContext() {
        var details = new PartnerInviteEmailService.MeetingInviteDetails(
                "Event", "BATbern61", LocalDate.of(2026, 9, 3), LocalTime.of(16, 0), LocalTime.of(17, 0), "Bern");

        service.sendCalendarInvites(
                List.of("partner@example.com", "organizer@batbern.ch"), details, new byte[]{1}, MEETING_ID, "organizer1");

        captureSentHtml("Einladung / Invitation:");
        MailContext sent = context.getValue();
        assertThat(sent.audience()).isEqualTo(MailContext.Audience.PARTNERS);
        assertThat(sent.templateKey()).isEqualTo("partner-meeting-invite");
        assertThat(sent.eventCode()).isEqualTo("BATbern61");
        assertThat(sent.senderUsername()).isEqualTo("organizer1");
        assertThat(sent.reference()).isEqualTo("partner-meeting:" + MEETING_ID);
        assertThat(sent.contentSource()).isEqualTo(MailContext.ContentSource.TEMPLATE);
        assertThat(sent.test()).isFalse();
    }

    @Test
    @DisplayName("16.9 AC1: the cancellation carries the cancellation template key and the same reference")
    void should_passCancellationContext() {
        service.sendCancellationNotice(List.of("partner@example.com", "organizer@batbern.ch"),
                "Event", "BATbern61", LocalDate.of(2026, 9, 3), new byte[]{9}, MEETING_ID, "organizer1");

        captureSentHtml("Absage / Cancellation:");
        MailContext sent = context.getValue();
        assertThat(sent.audience()).isEqualTo(MailContext.Audience.PARTNERS);
        assertThat(sent.templateKey()).isEqualTo("partner-meeting-cancellation");
        assertThat(sent.senderUsername()).isEqualTo("organizer1");
        assertThat(sent.reference()).isEqualTo("partner-meeting:" + MEETING_ID);
    }
}
