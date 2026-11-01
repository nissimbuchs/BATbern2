package ch.batbern.events.mailcenter;

import ch.batbern.events.mailcenter.MailMessageQueryService.MailEntry;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailFilter;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailPage;
import ch.batbern.events.mailcenter.api.generated.EventMailApi;
import ch.batbern.events.mailcenter.compose.ComposeRecipient;
import ch.batbern.events.mailcenter.compose.ComposeRequest;
import ch.batbern.events.mailcenter.compose.MailComposerService;
import ch.batbern.events.mailcenter.compose.MailComposerService.Preview;
import ch.batbern.events.mailcenter.compose.MailComposerService.SendResult;
import ch.batbern.events.mailcenter.compose.SkippedRecipient;
import ch.batbern.events.mailcenter.dto.generated.MailAudience;
import ch.batbern.events.mailcenter.dto.generated.MailComposeRecipient;
import ch.batbern.events.mailcenter.dto.generated.MailComposeRequest;
import ch.batbern.events.mailcenter.dto.generated.MailCounts;
import ch.batbern.events.mailcenter.dto.generated.MailMessageDetail;
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
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessagePage> listEventMails(String eventCode, MailAudience audience,
            String templateKey, LocalDate date, Boolean grouped, Boolean includeTests, Integer page, Integer limit) {
        MailFilter filter = new MailFilter(audience == null ? null : audience.getValue(), templateKey, date,
                !Boolean.FALSE.equals(grouped), Boolean.TRUE.equals(includeTests));
        MailPage result = queries.list(eventCode, filter, page, limit);
        return noStore(new MailMessagePage()
                .data(result.data().stream().map(EventMailController::summary).toList())
                .pagination(PaginationUtils.generateMetadata(page, limit, result.total())));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailCounts> getEventMailCounts(String eventCode, Boolean includeTests) {
        return noStore(new MailCounts().failedDeliveries(
                (int) queries.failedDeliveries(eventCode, Boolean.TRUE.equals(includeTests))));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessageDetail> getEventMail(String eventCode, String mailKey) {
        MailMessageQueryService.MailDetail detail = queries.get(eventCode, mailKey)
                .orElseThrow(() -> new NotFoundException("Mail not found: " + mailKey));
        return noStore(new MailMessageDetail()
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
                        .retryable(detail.progress().retryable())));
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
                .unknownPlaceholders(preview.unknownPlaceholders()));
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
                request.getVenueNotes());
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
                .venueNotes(send.getVenueNotes());
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
                .test(e.test());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
