package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.OutreachHistory;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.forwarding.ModeratorReplyAddress;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.repository.OutreachHistoryRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.types.SpeakerWorkflowState;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Own text to speakers (Story 16.5 AC11/AC12). The organizer picks speakers by status; each gets
 * the text with their own values, Reply-To the moderator and Cc their additional addresses, as a
 * reminder does. It is not a reminder: no reminder-log row and no escalation, one outreach-history
 * row per speaker.
 */
@Service
@RequiredArgsConstructor
public class SpeakerOwnTextMailService {

    private static final ZoneId ZURICH = ZoneId.of("Europe/Zurich");

    private final SpeakerPoolRepository speakerPoolRepository;
    private final SpeakerReminderEmailService reminderEmailService;
    private final ModeratorReplyAddress moderatorReplyAddress;
    private final OutreachHistoryRepository outreachHistoryRepository;
    private final EmailService emailService;

    @Value("${batbern.ses.configuration-set-name:#{null}}")
    private String configurationSetName;

    /** The event's speakers in one of {@code statuses}. */
    public List<SpeakerPool> audience(Event event, Collection<SpeakerWorkflowState> statuses) {
        return speakerPoolRepository.findByEventId(event.getId()).stream()
                .filter(speaker -> statuses.contains(speaker.getStatus()))
                .toList();
    }

    /**
     * The speaker's values, as a reminder has them. {@code deadline}: the response deadline while
     * INVITED, the content deadline otherwise, the event date when none is set.
     */
    public Map<String, String> variables(SpeakerPool speaker, Event event, String lang) {
        return reminderEmailService.variables(speaker, event, deadline(speaker, event),
                "en".equals(lang) ? Locale.ENGLISH : Locale.GERMAN);
    }

    LocalDate deadline(SpeakerPool speaker, Event event) {
        LocalDate deadline = speaker.getStatus() == SpeakerWorkflowState.INVITED
                ? speaker.getResponseDeadline() : speaker.getContentDeadline();
        return deadline != null ? deadline : event.getDate().atZone(ZURICH).toLocalDate();
    }

    public String replyTo(Event event) {
        return moderatorReplyAddress.forEvent(event);
    }

    /** One speaker's mail; the outreach history notes it with its subject. */
    public void send(MailContext context, SpeakerPool speaker, String to, List<String> cc, RenderedMail mail,
            String replyTo, String organizerUsername) {
        String subject = mail.subject();
        emailService.sendHtmlEmailSync(context, to, cc, subject, mail.html(), configurationSetName, replyTo);
        OutreachHistory outreach = new OutreachHistory();
        outreach.setSpeakerPoolId(speaker.getId());
        outreach.setContactDate(Instant.now());
        outreach.setContactMethod("manual_email");
        outreach.setNotes("Own text: " + subject);
        outreach.setOrganizerUsername(organizerUsername);
        outreachHistoryRepository.save(outreach);
    }
}
