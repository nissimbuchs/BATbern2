package ch.batbern.events.mailcenter;

import ch.batbern.events.mailcenter.MailMessageQueryService.MailEntry;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailFilter;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailPage;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailScope;
import ch.batbern.events.mailcenter.api.generated.EventMailApi;
import ch.batbern.events.mailcenter.compose.ComposeRecipient;
import ch.batbern.events.mailcenter.compose.ComposeRequest;
import ch.batbern.events.mailcenter.compose.MailComposerService;
import ch.batbern.events.mailcenter.compose.MailComposerService.Preview;
import ch.batbern.events.mailcenter.compose.MailComposerService.SendResult;
import ch.batbern.events.mailcenter.compose.SkippedRecipient;
import ch.batbern.events.mailcenter.inbound.AutomaticMailCounter;
import ch.batbern.events.mailcenter.inbound.InboundMailCenter;
import ch.batbern.events.mailcenter.dto.generated.MailAudience;
import ch.batbern.events.mailcenter.dto.generated.MailComposeRecipient;
import ch.batbern.events.mailcenter.dto.generated.MailAlreadySent;
import ch.batbern.events.mailcenter.dto.generated.MailComposeRequest;
import ch.batbern.events.mailcenter.dto.generated.MailCounts;
import ch.batbern.events.mailcenter.dto.generated.MailMessageDetail;
import ch.batbern.events.mailcenter.dto.generated.MailMessagePatch;
import ch.batbern.events.mailcenter.dto.generated.MailMessagePage;
import ch.batbern.events.mailcenter.dto.generated.MailMessageSummary;
import ch.batbern.events.mailcenter.dto.generated.MailPreviewResponse;
import ch.batbern.events.mailcenter.dto.generated.MailProgress;
import ch.batbern.events.mailcenter.dto.generated.MailRecipientEntry;
import ch.batbern.events.mailcenter.dto.generated.MailSampleRecipient;
import ch.batbern.events.mailcenter.dto.generated.MailSendRequest;
import ch.batbern.events.mailcenter.dto.generated.MailSendResponse;
import ch.batbern.events.mailcenter.dto.generated.MailSkippedRecipient;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.NotFoundException;
import ch.batbern.shared.exception.ValidationException;
import ch.batbern.shared.mail.MailContext;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Story 16.3: what went out for an event (Mail Center); Story 16.8: preview, send and retry through
 * the one composer. Implements the generated {@link EventMailApi} contract
 * (event-mail-api.openapi.yml). Mail bodies and addresses are personal data: ORGANIZER only, never
 * cached.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventMailController implements EventMailApi {

    private final MailMessageQueryService queries;
    private final MailComposerService composer;
    private final MailParticipantResolver participants;
    private final InboundMailCenter inbound;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessagePage> listEventMails(String eventCode, MailAudience audience,
            String templateKey, String reference, LocalDate date, Boolean grouped, Boolean includeTests,
            String direction, Boolean spam, Integer page, Integer limit) {
        MailFilter filter = filter(audience, templateKey, reference, date, grouped, includeTests)
                .with(direction, Boolean.TRUE.equals(spam));
        return page(queries.list(eventCode, filter, page, limit), page, limit);
    }

    /** Story 16.7 AC9 and Story 16.10 AC4: the same list without the event in the path. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessagePage> listMails(String eventCode, Boolean noEvent, String participantUsername,
            String participantCompany, MailAudience audience, String templateKey, String reference, LocalDate date,
            Boolean grouped, Boolean includeTests, String direction, Boolean spam, Integer page, Integer limit) {
        boolean withoutEvent = Boolean.TRUE.equals(noEvent);
        if (eventCode != null && withoutEvent) {
            throw new ValidationException("noEvent", "eventCode and noEvent exclude each other");
        }
        if (participantUsername != null && participantCompany != null) {
            throw new ValidationException("participantUsername",
                    "participantUsername and participantCompany exclude each other");
        }
        List<String> addresses = participantUsername != null ? participants.ofUser(participantUsername)
                : participantCompany != null ? participants.ofCompany(participantCompany) : null;
        MailFilter filter = filter(audience, templateKey, reference, date, grouped, includeTests)
                .with(direction, Boolean.TRUE.equals(spam));
        return page(queries.list(new MailScope(eventCode, withoutEvent, addresses), filter, page, limit), page, limit);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessageDetail> getMail(String mailKey) {
        return noStore(detail(queries.get(mailKey).orElseThrow(() -> new NotFoundException("Mail", mailKey))));
    }

    /** Story 16.7 AC3: "Not spam" threads the mail like a new arrival. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessageDetail> patchMail(String mailKey, MailMessagePatch patch) {
        if (!Boolean.FALSE.equals(patch.getSpam())) {
            throw new ValidationException("spam", "Only spam=false is accepted");
        }
        inbound.releaseSpam(mailKey);
        return getMail(mailKey);
    }

    private static MailFilter filter(MailAudience audience, String templateKey, String reference, LocalDate date,
            Boolean grouped, Boolean includeTests) {
        return new MailFilter(audience == null ? null : audience.getValue(), templateKey, reference, date,
                !Boolean.FALSE.equals(grouped), Boolean.TRUE.equals(includeTests));
    }

    private static ResponseEntity<MailMessagePage> page(MailPage result, int page, int limit) {
        return noStore(new MailMessagePage()
                .data(result.data().stream().map(EventMailController::summary).toList())
                .pagination(PaginationUtils.generateMetadata(page, limit, result.total())));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailCounts> getEventMailCounts(String eventCode, Boolean includeTests) {
        Map<String, Long> automatic = queries.automaticCounts(eventCode);
        return noStore(new MailCounts()
                .failedDeliveries((int) queries.failedDeliveries(eventCode, Boolean.TRUE.equals(includeTests)))
                .outOfOffice(count(automatic, AutomaticMailCounter.Kind.OUT_OF_OFFICE))
                .bounces(count(automatic, AutomaticMailCounter.Kind.BOUNCE))
                .calendarAccepts(count(automatic, AutomaticMailCounter.Kind.CALENDAR_ACCEPTED))
                .calendarDeclines(count(automatic, AutomaticMailCounter.Kind.CALENDAR_DECLINED))
                .keywordAccepts(count(automatic, AutomaticMailCounter.Kind.KEYWORD_ACCEPT))
                .keywordCancels(count(automatic, AutomaticMailCounter.Kind.KEYWORD_CANCEL))
                .unsubscribes(count(automatic, AutomaticMailCounter.Kind.UNSUBSCRIBE)));
    }

    private static int count(Map<String, Long> counts, AutomaticMailCounter.Kind kind) {
        return counts.getOrDefault(kind.name(), 0L).intValue();
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessageDetail> getEventMail(String eventCode, String mailKey) {
        return noStore(detail(queries.get(eventCode, mailKey)
                .orElseThrow(() -> new NotFoundException("Mail not found: " + mailKey))));
    }

    private static MailMessageDetail detail(MailMessageQueryService.MailDetail detail) {
        return new MailMessageDetail()
                .message(summary(detail.entry()))
                .bodyHtml(detail.bodyHtml())
                .recipients(detail.recipients().stream()
                        .map(r -> new MailRecipientEntry()
                                .address(r.address())
                                .kind(MailRecipientEntry.KindEnum.fromValue(r.kind()))
                                .status(MailRecipientEntry.StatusEnum.fromValue(r.status()))
                                .failureReason(r.failureReason()))
                        .toList())
                .progress(detail.progress() == null ? null : new MailProgress()
                        .status(MailProgress.StatusEnum.fromValue(detail.progress().status()))
                        .total(detail.progress().total())
                        .sent(detail.progress().sent())
                        .failed(detail.progress().failed())
                        .retryable(detail.progress().retryable()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailPreviewResponse> previewEventMail(String eventCode, MailComposeRequest request) {
        Preview preview = composer.preview(eventCode, composeRequest(request));
        ComposeRecipient recipient = preview.recipient();
        return noStore(new MailPreviewResponse()
                .subject(preview.subject())
                .html(preview.html())
                .recipientIndex(preview.recipientIndex())
                .recipientCount(preview.recipientCount())
                .recipient(new MailComposeRecipient().displayName(recipient.displayName())
                        .address(recipient.address()).cc(recipient.cc()))
                .sampleRecipients(preview.sampleRecipients().stream()
                        .map(name -> new MailSampleRecipient().displayName(name)).toList())
                .personalized(preview.personalized())
                .templateKey(preview.templateKey())
                .locale(preview.locale())
                .replyTo(preview.replyTo())
                .organizerCount(preview.organizerCount())
                .skipped(skipped(preview.skipped()))
                .unknownPlaceholders(preview.unknownPlaceholders())
                .highlightedSubject(preview.highlightedSubject())
                .highlightedHtml(preview.highlightedHtml())
                .offeredVariables(preview.offeredVariables())
                .allowedVariables(preview.allowedVariables())
                .alreadySent(preview.alreadySent() == null ? null
                        : new MailAlreadySent().sentAt(preview.alreadySent().atOffset(ZoneOffset.UTC))));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailSendResponse> sendEventMail(String eventCode, MailSendRequest request) {
        SendResult result = composer.send(eventCode, composeRequest(compose(request)),
                MailComposerService.Target.valueOf(request.getTarget().getValue()),
                securityContextHelper.getCurrentUsername());
        return accepted(result);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailSendResponse> retryEventMail(String eventCode, String mailKey) {
        return accepted(composer.retry(eventCode, mailKey, securityContextHelper.getCurrentUsername()));
    }

    private static ComposeRequest composeRequest(MailComposeRequest request) {
        return new ComposeRequest(
                MailContext.Audience.valueOf(request.getAudience().getValue()),
                request.getContentSource() == null ? null
                        : MailContext.ContentSource.valueOf(request.getContentSource().getValue()),
                request.getTemplateKey(),
                request.getLocale() == null ? null : request.getLocale().getValue(),
                request.getRecipientIndex() == null ? 0 : request.getRecipientIndex(),
                Boolean.TRUE.equals(request.getNewsletterReminder()),
                request.getSpeakerReminderType() == null ? null : request.getSpeakerReminderType().getValue(),
                request.getVenueRecipients() == null ? List.of()
                        : request.getVenueRecipients().stream().map(r -> r.getValue()).toList(),
                request.getVenueNotes(),
                request.getOwnSubject(),
                request.getOwnBodyHtml(),
                request.getSpeakerStatuses() == null ? List.of()
                        : request.getSpeakerStatuses().stream().map(st -> st.getValue()).toList(),
                Boolean.TRUE.equals(request.getConfirmResend()));
    }

    /** The send DTO is a separate generated class with the compose fields plus {@code target}. */
    private static MailComposeRequest compose(MailSendRequest send) {
        return new MailComposeRequest()
                .audience(MailComposeRequest.AudienceEnum.fromValue(send.getAudience().getValue()))
                .contentSource(send.getContentSource() == null ? null
                        : MailComposeRequest.ContentSourceEnum.fromValue(send.getContentSource().getValue()))
                .templateKey(send.getTemplateKey())
                .locale(send.getLocale() == null ? null
                        : MailComposeRequest.LocaleEnum.fromValue(send.getLocale().getValue()))
                .recipientIndex(send.getRecipientIndex())
                .newsletterReminder(send.getNewsletterReminder())
                .speakerReminderType(send.getSpeakerReminderType() == null ? null
                        : MailComposeRequest.SpeakerReminderTypeEnum.fromValue(
                                send.getSpeakerReminderType().getValue()))
                .venueRecipients(send.getVenueRecipients() == null ? null
                        : send.getVenueRecipients().stream()
                                .map(r -> MailComposeRequest.VenueRecipientsEnum.fromValue(r.getValue())).toList())
                .venueNotes(send.getVenueNotes())
                .ownSubject(send.getOwnSubject())
                .ownBodyHtml(send.getOwnBodyHtml())
                .speakerStatuses(send.getSpeakerStatuses() == null ? null
                        : send.getSpeakerStatuses().stream()
                                .map(st -> MailComposeRequest.SpeakerStatusesEnum.fromValue(st.getValue())).toList())
                .confirmResend(send.getConfirmResend());
    }

    private static List<MailSkippedRecipient> skipped(List<SkippedRecipient> skipped) {
        return skipped.stream()
                .map(s -> new MailSkippedRecipient().displayName(s.displayName())
                        .reason(MailSkippedRecipient.ReasonEnum.fromValue(s.reason())))
                .toList();
    }

    private static ResponseEntity<MailSendResponse> accepted(SendResult result) {
        return ResponseEntity.status(HttpStatus.ACCEPTED).cacheControl(CacheControl.noStore())
                .body(new MailSendResponse()
                        .mailKeys(result.mailKeys())
                        .target(MailSendResponse.TargetEnum.fromValue(result.target().name()))
                        .recipientCount(result.recipientCount())
                        .skipped(skipped(result.skipped())));
    }

    private static MailMessageSummary summary(MailEntry e) {
        return new MailMessageSummary()
                .mailKey(e.mailKey())
                .sentAt(e.sentAt().atOffset(ZoneOffset.UTC))
                .senderUsername(e.senderUsername())
                .audience(MailAudience.fromValue(e.audience()))
                .templateKey(e.templateKey())
                .contentSource(MailMessageSummary.ContentSourceEnum.fromValue(e.contentSource()))
                .subject(e.subject())
                .recipientCount(e.recipientCount())
                .deliveredCount(e.deliveredCount())
                .failedCount(e.failedCount())
                .messageCount(e.messageCount())
                .newsletterSendId(e.newsletterSendId())
                .newsletterStatus(e.newsletterStatus())
                .test(e.test())
                .reference(e.reference())
                .direction(e.direction() == null ? null : MailMessageSummary.DirectionEnum.fromValue(e.direction()))
                .fromAddress(e.fromAddress())
                .fromName(e.fromName())
                .eventCode(e.eventCode())
                .conversationKey(e.conversationKey())
                .spam(e.spam())
                .participantStatus(e.participantStatus() == null ? null
                        : MailMessageSummary.ParticipantStatusEnum.fromValue(e.participantStatus()));
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
