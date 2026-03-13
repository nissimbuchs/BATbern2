package ch.batbern.partners.service;

import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
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

        String subject = "Einladung: BATbern Partner-Meeting + " + eventTitle;
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

        String subject = "Absage: BATbern Partner-Meeting + " + eventTitle;
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

    private String buildCancellationBody(String eventTitle, LocalDate meetingDate) {
        return "<html><body>"
                + "<p>Sehr geehrte Damen und Herren,</p>"
                + "<p>das BATbern Partner-Meeting vom <strong>" + DATE_FMT.format(meetingDate) + "</strong>"
                + " im Zusammenhang mit dem Event <em>" + eventTitle + "</em> wurde abgesagt.</p>"
                + "<p>Bitte importieren Sie den beigefügten Kalendertermin (.ics),"
                + " um den Eintrag aus Ihrem Kalender zu entfernen.</p>"
                + "<p>Mit freundlichen Grüssen,<br/>" + organizerName + "</p>"
                + "</body></html>";
    }

    private String buildEmailBody(
            String eventTitle,
            LocalDate meetingDate,
            LocalTime startTime,
            LocalTime endTime,
            String location
    ) {
        return "<html><body>"
                + "<p>Sehr geehrte Damen und Herren,</p>"
                + "<p>wir laden Sie herzlich zu unserem Partner-Meeting ein.</p>"
                + "<table>"
                + "<tr><td><strong>Datum:</strong></td><td>" + DATE_FMT.format(meetingDate) + "</td></tr>"
                + "<tr><td><strong>Zeit:</strong></td><td>"
                + TIME_FMT.format(startTime) + " – " + TIME_FMT.format(endTime) + " Uhr</td></tr>"
                + (location != null && !location.isBlank()
                        ? "<tr><td><strong>Ort:</strong></td><td>" + location + "</td></tr>" : "")
                + "</table>"
                + "<p>Im Anschluss findet das BATbern Event <em>" + eventTitle + "</em> statt.</p>"
                + "<p>Bitte importieren Sie den beigefügten Kalendertermin (.ics) in Ihren Kalender.</p>"
                + "<p>Mit freundlichen Grüssen,<br/>" + organizerName + "</p>"
                + "</body></html>";
    }
}
