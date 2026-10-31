package ch.batbern.events.mailcenter;

import ch.batbern.events.mailcenter.MailMessageQueryService.MailEntry;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailFilter;
import ch.batbern.events.mailcenter.MailMessageQueryService.MailPage;
import ch.batbern.events.mailcenter.api.generated.EventMailApi;
import ch.batbern.events.mailcenter.dto.generated.MailAudience;
import ch.batbern.events.mailcenter.dto.generated.MailCounts;
import ch.batbern.events.mailcenter.dto.generated.MailMessageDetail;
import ch.batbern.events.mailcenter.dto.generated.MailMessagePage;
import ch.batbern.events.mailcenter.dto.generated.MailMessageSummary;
import ch.batbern.events.mailcenter.dto.generated.MailRecipientEntry;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneOffset;

/**
 * Story 16.3: what went out for an event (Mail Center). Implements the generated
 * {@link EventMailApi} contract (event-mail-api.openapi.yml). Mail bodies and addresses are
 * personal data: ORGANIZER only, never cached.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class EventMailController implements EventMailApi {

    private final MailMessageQueryService queries;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessagePage> listEventMails(String eventCode, MailAudience audience,
            String templateKey, LocalDate date, Boolean grouped, Integer page, Integer limit) {
        MailFilter filter = new MailFilter(audience == null ? null : audience.getValue(), templateKey, date,
                !Boolean.FALSE.equals(grouped));
        MailPage result = queries.list(eventCode, filter, page, limit);
        return noStore(new MailMessagePage()
                .data(result.data().stream().map(EventMailController::summary).toList())
                .pagination(PaginationUtils.generateMetadata(page, limit, result.total())));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailCounts> getEventMailCounts(String eventCode) {
        return noStore(new MailCounts().failedDeliveries((int) queries.failedDeliveries(eventCode)));
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
                        .toList()));
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
