package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.event.SpeakerProposalSubmittedEvent;
import ch.batbern.events.forwarding.EmailForwardingProperties;
import ch.batbern.events.forwarding.EmailForwardingService;
import ch.batbern.events.forwarding.ForwardingDirectory;
import ch.batbern.events.mailcenter.inbound.InboundMail;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.mailcenter.inbound.InboundMailStore;
import ch.batbern.events.mailcenter.render.MailLocales;
import ch.batbern.events.mailcenter.render.MailPipeline;
import ch.batbern.events.mailcenter.render.RenderedMail;
import ch.batbern.events.mailcenter.variables.MailVariable;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.MailTrigger;
import ch.batbern.shared.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * A speaker proposal ("I could speak on that") in the Mail Center, after its commit:
 * <ol>
 *   <li>an OPEN conversation on the event, opened by one inbound message from the person with the
 *       proposed title and abstract, so organizers see it under "Open" and answer it there;</li>
 *   <li>the confirmation to the person (template {@code mailcenter-speaker-proposal}, DE + EN by
 *       their language), recorded in that conversation, with the conversation's reply address so an
 *       answer lands in it;</li>
 *   <li>the new-mail notice to every organizer, as for mail to the aliases.</li>
 * </ol>
 * One person about their own proposal, and organizers: no audience mail (#1179). A failure is
 * logged and counted and never undoes the proposal. Idempotent by the proposal id.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SpeakerProposalNotices {

    static final String TEMPLATE_KEY = "mailcenter-speaker-proposal";
    static final String FAILED_METRIC = "SpeakerProposalNoticeFailed";

    private final UserApiClient userApiClient;
    private final EventRepository eventRepository;
    private final InboundMailStore store;
    private final MailPipeline mailPipeline;
    private final EmailService emailService;
    private final ConversationNotices conversationNotices;
    private final ForwardingDirectory directory;
    private final EmailForwardingProperties forwarding;
    private final NamedParameterJdbcTemplate jdbc;
    private final MailCenterMetrics metrics;

    @Async
    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onProposalSubmitted(SpeakerProposalSubmittedEvent proposal) {
        try {
            Optional<Event> event = eventRepository.findByEventCode(proposal.eventCode());
            UserResponse person = userApiClient.getUserByUsername(proposal.username());
            String address = person.getEmail() == null ? null : person.getEmail().trim().toLowerCase(Locale.ROOT);
            if (event.isEmpty() || address == null || address.isBlank()) {
                log.error("Speaker proposal {} not confirmed: event or address missing", proposal.proposalId());
                metrics.increment(FAILED_METRIC);
                return;
            }
            String subject = "Referatsvorschlag: " + proposal.title();
            String groupId = "speaker-proposal-" + proposal.proposalId();
            String key;
            try {
                key = store.store(openingMail(proposal, address, subject), groupId, null, null,
                        new InboundMailStore.Placement(null, proposal.eventCode(),
                                MailContext.Audience.SPEAKERS.name(), null));
            } catch (InboundMailStore.DuplicateInboundMailException e) {
                log.info("Speaker proposal {} is in the Mail Center already", proposal.proposalId());
                return;
            }
            confirm(proposal, event.get(), person, address, key);
            noticeOrganizers(new InboundMailCenter.Stored(key, proposal.eventCode(), proposal.speakerName(),
                    address, subject));
            log.info("Speaker proposal {} confirmed and opened as conversation {}", proposal.proposalId(), key);
        } catch (RuntimeException e) {
            log.error("Speaker proposal {} not confirmed: {}", proposal.proposalId(), e.toString(), e);
            metrics.increment(FAILED_METRIC);
        }
    }

    private static InboundMail openingMail(SpeakerProposalSubmittedEvent proposal, String address, String subject) {
        String text = proposal.speakerName() + " schlägt über die Website ein Referat für " + proposal.eventCode()
                + " vor. / proposes a talk on the website.\n\n"
                + proposal.title() + "\n\n"
                + (proposal.abstractText() == null ? "" : proposal.abstractText().strip());
        return new InboundMail(null, null, List.of(), address, proposal.speakerName(), null, List.of(), List.of(),
                subject, text, null, List.of(), "text/plain", null, null, null, null, null, false);
    }

    private void confirm(SpeakerProposalSubmittedEvent proposal, Event event, UserResponse person, String address,
            String conversationKey) {
        String locale = MailLocales.languageOf(userApiClient.getPreferredLanguage(proposal.username()));
        String firstName = person.getFirstName() != null && !person.getFirstName().isBlank()
                ? person.getFirstName().trim() : proposal.speakerName();
        RenderedMail mail = mailPipeline.renderNotice(mailPipeline.template(TEMPLATE_KEY, locale),
                mailPipeline.variables().event(event, locale)
                        .put(MailVariable.RECIPIENT_NAME, firstName)
                        .put(MailVariable.SESSION_TITLE, proposal.title())
                        .asMap());
        String subject = mail.subject() != null ? mail.subject()
                : ("en".equals(locale) ? "Your talk proposal: " : "Ihr Referatsvorschlag: ") + proposal.title();
        MailContext context = MailContext.of(MailTrigger.system(), proposal.eventCode(),
                MailContext.Audience.SPEAKERS, TEMPLATE_KEY);
        String token = jdbc.queryForObject("SELECT reply_token FROM mail_conversation WHERE conversation_key = :key",
                new MapSqlParameterSource("key", conversationKey), String.class);
        emailService.sendHtmlEmailSync(context, address, null, subject, mail.html(),
                "replies+" + token + "@" + forwarding.domain());
        jdbc.update("""
                UPDATE mail_message SET conversation_id =
                       (SELECT id FROM mail_conversation WHERE conversation_key = :key)
                 WHERE send_group_id = :groupId AND conversation_id IS NULL
                """, new MapSqlParameterSource("key", conversationKey).addValue("groupId", context.sendGroupId()));
    }

    private void noticeOrganizers(InboundMailCenter.Stored stored) {
        Function<String, EmailForwardingService.Notice> notices = conversationNotices.forStored(stored);
        for (String organizer : directory.organizerEmails()) {
            try {
                EmailForwardingService.Notice notice = notices.apply(organizer);
                MailContext context = MailContext.of(MailTrigger.system(), stored.eventCode(),
                        MailContext.Audience.ORGANIZERS, ConversationNotices.TEMPLATE_KEY);
                emailService.sendHtmlEmailSync(context, organizer, null, notice.subject(), notice.html(), null);
            } catch (RuntimeException e) {
                log.error("Speaker proposal notice to an organizer failed: {}", e.toString());
                metrics.increment(FAILED_METRIC);
            }
        }
    }
}
