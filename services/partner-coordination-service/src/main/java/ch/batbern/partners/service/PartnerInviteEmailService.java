package ch.batbern.partners.service;

import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.Year;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service for sending partner meeting calendar invites via AWS SES.
 *
 * Story 8.3: AC3 (ICS email), AC8 (async send — 202 response immediately)
 *
 * Sends an HTML email with the .ics file attached (Content-Type: text/calendar).
 * Delegates to shared-kernel EmailService which handles SES raw email.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartnerInviteEmailService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy");
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private final EmailService emailService;

    @Value("${app.email.organizer-name:BATbern Organisationsteam}")
    private String organizerName;

    /** Public site base — used for the BATbern logo in the email header (mirrors the EMS layout). */
    @Value("${app.base-url:https://batbern.ch}")
    private String baseUrl;

    /**
     * Groups the meeting-specific fields needed to send a calendar invite or cancellation.
     *
     * @param eventTitle      title of the linked BATbern event
     * @param eventCode       BATbern event code (e.g. "BATbern57") used in filename + subject
     * @param meetingDate     date of the partner meeting
     * @param startTime       meeting start time
     * @param endTime         meeting end time
     * @param location        meeting location (may be null or blank)
     */
    public record MeetingInviteDetails(
            String eventTitle,
            String eventCode,
            LocalDate meetingDate,
            LocalTime startTime,
            LocalTime endTime,
            String location
    ) { }

    /**
     * Send the partner meeting calendar invite asynchronously.
     *
     * Sends a single email addressed to all recipients (partners + organizers) so that
     * every invitee can see who else received the invitation. The ICS attachment contains
     * two VEVENTs: the partner meeting and the main BATbern event.
     *
     * @param recipientEmails list of all recipient email addresses (partners + organizers)
     * @param details         meeting details (title, code, date, times, location)
     * @param icsContent      generated ICS file bytes
     */
    @Async
    public void sendCalendarInvites(
            List<String> recipientEmails,
            MeetingInviteDetails details,
            byte[] icsContent
    ) {
        String eventTitle = details.eventTitle();
        String eventCode = details.eventCode();
        LocalDate meetingDate = details.meetingDate();
        LocalTime meetingStartTime = details.startTime();
        LocalTime meetingEndTime = details.endTime();
        String location = details.location();
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            log.warn("No recipient emails provided — skipping partner meeting invite send");
            return;
        }

        String subject = "Einladung / Invitation: BATbern Partner-Meeting + " + eventTitle;
        String htmlBody = buildEmailBody(eventTitle, meetingDate, meetingStartTime, meetingEndTime, location);

        String filename = "partner-meeting-and-" + eventCode.toLowerCase() + "-event.ics";
        EmailService.EmailAttachment icsAttachment = new EmailService.EmailAttachment(
                filename,
                icsContent,
                "text/calendar; charset=utf-8; method=REQUEST",
                true  // inline → triggers accept/decline in Apple Mail, Outlook, Gmail
        );

        // Send one email with all recipients in To: so everyone sees who was invited.
        // InternetAddress.parse() (used by EmailService) accepts comma-separated addresses.
        String toField = String.join(",", recipientEmails);

        log.info("Sending partner meeting invite to {} recipients for event={}", recipientEmails.size(), eventTitle);

        try {
            emailService.sendHtmlEmailWithAttachments(
                    toField,
                    subject,
                    htmlBody,
                    List.of(icsAttachment)
            );
            log.debug("Partner meeting invite sent to: {}", toField);
        } catch (Exception e) {
            log.error("Failed to send partner meeting invite: {}", e.getMessage(), e);
        }
    }

    /**
     * Send a cancellation notice with METHOD:CANCEL ICS so calendar clients remove the entry.
     *
     * @param recipientEmails same list as used for the original invite
     * @param eventTitle      title of the linked BATbern event (for subject/body)
     * @param eventCode       BATbern event code (e.g. "BATbern57") used in the filename
     * @param meetingDate     date of the meeting being cancelled
     * @param cancelIcsContent ICS with METHOD:CANCEL generated by IcsGeneratorService
     */
    @Async
    public void sendCancellationNotice(
            List<String> recipientEmails,
            String eventTitle,
            String eventCode,
            LocalDate meetingDate,
            byte[] cancelIcsContent
    ) {
        if (recipientEmails == null || recipientEmails.isEmpty()) {
            log.warn("No recipient emails provided — skipping cancellation notice");
            return;
        }

        String subject = "Absage / Cancellation: BATbern Partner-Meeting + " + eventTitle;
        String htmlBody = buildCancellationBody(eventTitle, meetingDate);
        String filename = "partner-meeting-and-" + eventCode.toLowerCase() + "-event.ics";

        EmailService.EmailAttachment attachment = new EmailService.EmailAttachment(
                filename,
                cancelIcsContent,
                "text/calendar; charset=utf-8; method=CANCEL",
                true  // inline → calendar clients process the cancellation automatically
        );

        String toField = String.join(",", recipientEmails);
        log.info("Sending cancellation notice to {} recipients for event={}", recipientEmails.size(), eventTitle);

        try {
            emailService.sendHtmlEmailWithAttachments(toField, subject, htmlBody, List.of(attachment));
            log.debug("Cancellation notice sent to: {}", toField);
        } catch (Exception e) {
            log.error("Failed to send cancellation notice: {}", e.getMessage(), e);
        }
    }

    private String buildEmailBody(
            String eventTitle,
            LocalDate meetingDate,
            LocalTime startTime,
            LocalTime endTime,
            String location
    ) {
        String title = esc(eventTitle);
        String date = DATE_FMT.format(meetingDate);
        String time = TIME_FMT.format(startTime) + " – " + TIME_FMT.format(endTime);
        String loc = (location != null && !location.isBlank()) ? esc(location) : null;

        String de = "<p>Sehr geehrte Damen und Herren,</p>"
                + "<p>wir laden Sie herzlich zu unserem Partner-Meeting ein.</p>"
                + card(row("Datum", date)
                        + row("Zeit", time + " Uhr")
                        + (loc != null ? row("Ort", loc) : ""))
                + "<p>Im Anschluss findet das BATbern Event <em>" + title + "</em> statt.</p>"
                + "<p>Bitte importieren Sie den beigefügten Kalendertermin (.ics) in Ihren Kalender.</p>"
                + "<p>Mit freundlichen Grüssen,<br>" + esc(organizerName) + "</p>";

        String en = "<p>Dear Sir or Madam,</p>"
                + "<p>we warmly invite you to our partner meeting.</p>"
                + card(row("Date", date)
                        + row("Time", time)
                        + (loc != null ? row("Location", loc) : ""))
                + "<p>The BATbern event <em>" + title + "</em> takes place immediately afterwards.</p>"
                + "<p>Please import the attached calendar entry (.ics) into your calendar.</p>"
                + "<p>Kind regards,<br>" + esc(organizerName) + "</p>";

        return wrapInLayout(de + divider() + en);
    }

    private String buildCancellationBody(String eventTitle, LocalDate meetingDate) {
        String title = esc(eventTitle);
        String date = DATE_FMT.format(meetingDate);

        String de = "<p>Sehr geehrte Damen und Herren,</p>"
                + "<p>das BATbern Partner-Meeting vom <strong>" + date + "</strong>"
                + " im Zusammenhang mit dem Event <em>" + title + "</em> wurde abgesagt.</p>"
                + "<p>Bitte importieren Sie den beigefügten Kalendertermin (.ics),"
                + " um den Eintrag aus Ihrem Kalender zu entfernen.</p>"
                + "<p>Mit freundlichen Grüssen,<br>" + esc(organizerName) + "</p>";

        String en = "<p>Dear Sir or Madam,</p>"
                + "<p>the BATbern partner meeting on <strong>" + date + "</strong>"
                + " associated with the event <em>" + title + "</em> has been cancelled.</p>"
                + "<p>Please import the attached calendar entry (.ics) to remove it from your calendar.</p>"
                + "<p>Kind regards,<br>" + esc(organizerName) + "</p>";

        return wrapInLayout(de + divider() + en);
    }

    // ── Presentation helpers ──────────────────────────────────────────────
    // The partner service has no DB-backed template engine or shared layout, so the
    // batbern-default look is reproduced here with inline CSS (the only place inline
    // styling is appropriate). Mirrors the event-management layout: blue header with the
    // BATbern logo, a white card, an event-detail card, and a muted footer.

    private String wrapInLayout(String content) {
        return "<!DOCTYPE html><html lang=\"de\"><head><meta charset=\"UTF-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">"
                + "<title>BATbern</title></head>"
                + "<body style=\"margin:0;padding:0;background-color:#f4f4f4;\">"
                + "<table width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\""
                + " style=\"background-color:#f4f4f4;width:100%;\"><tr><td align=\"center\" style=\"padding:20px;\">"
                + "<table width=\"600\" cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\""
                + " style=\"width:600px;max-width:600px;background-color:#ffffff;border-radius:8px;overflow:hidden;\">"
                + "<tr><td style=\"background-color:#1976d2;text-align:center;padding:30px 20px 20px;\">"
                + "<img src=\"" + baseUrl + "/BATbern_white_logo.png\" alt=\"BATbern\" width=\"180\""
                + " style=\"width:180px;max-width:180px;height:auto;border:0;display:block;margin:0 auto;\""
                + " onerror=\"this.style.display='none'\">"
                + "<div style=\"color:#ffffff;font-family:'Helvetica Neue',Arial,sans-serif;"
                + "font-size:15px;font-weight:600;margin-top:8px;\">BATbern</div></td></tr>"
                + "<tr><td style=\"padding:32px 36px 8px;font-family:'Helvetica Neue',Arial,sans-serif;"
                + "color:#444;font-size:15px;line-height:1.7;\">" + content + "</td></tr>"
                + "<tr><td style=\"padding:20px 36px 24px;border-top:1px solid #eee;"
                + "font-family:'Helvetica Neue',Arial,sans-serif;font-size:11px;color:#999;text-align:center;\">"
                + "&copy; " + Year.now().getValue() + " BATbern. Alle Rechte vorbehalten. / All rights reserved."
                + "</td></tr></table></td></tr></table></body></html>";
    }

    private String card(String rows) {
        return "<table cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\""
                + " style=\"width:100%;background-color:#f5f7fa;border-radius:7px;margin:16px 0;\">"
                + "<tr><td style=\"padding:16px 20px;\">"
                + "<table cellpadding=\"0\" cellspacing=\"0\" role=\"presentation\" style=\"width:100%;\">"
                + rows + "</table></td></tr></table>";
    }

    private String row(String label, String value) {
        return "<tr><td style=\"padding:4px 16px 4px 0;font-size:11px;font-weight:700;letter-spacing:1.5px;"
                + "text-transform:uppercase;color:#888;white-space:nowrap;vertical-align:top;\">" + label + "</td>"
                + "<td style=\"padding:4px 0;font-size:15px;color:#222;\">" + value + "</td></tr>";
    }

    private String divider() {
        return "<hr style=\"border:none;border-top:1px solid #eee;margin:28px 0;\">";
    }

    /** Minimal HTML-escaping for values interpolated into the email body. */
    private String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
