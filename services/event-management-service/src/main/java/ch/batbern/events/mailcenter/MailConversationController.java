package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailConversation;
import ch.batbern.events.mailcenter.ConversationQueryService.ConversationEntry;
import ch.batbern.events.mailcenter.ConversationQueryService.ConversationMessage;
import ch.batbern.events.mailcenter.ConversationQueryService.ConversationPage;
import ch.batbern.events.mailcenter.api.generated.MailConversationApi;
import ch.batbern.events.mailcenter.dto.generated.MailAttachment;
import ch.batbern.events.mailcenter.dto.generated.DownloadUrlResponse;
import ch.batbern.events.mailcenter.dto.generated.MailAudience;
import ch.batbern.events.mailcenter.dto.generated.MailConversationCounts;
import ch.batbern.events.mailcenter.dto.generated.MailConversationDetail;
import ch.batbern.events.mailcenter.dto.generated.MailConversationPage;
import ch.batbern.events.mailcenter.dto.generated.MailConversationPatch;
import ch.batbern.events.mailcenter.dto.generated.MailConversationReply;
import ch.batbern.events.mailcenter.dto.generated.MailConversationStatus;
import ch.batbern.events.mailcenter.dto.generated.MailConversationSummary;
import ch.batbern.events.mailcenter.dto.generated.MailMessageDetail;
import ch.batbern.events.mailcenter.dto.generated.MailMessageSummary;
import ch.batbern.events.security.SecurityContextHelper;
import ch.batbern.shared.api.PaginationUtils;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneOffset;
import java.util.List;

/**
 * Story 16.2a: conversations ("Needs an answer"). Implements the generated
 * {@link MailConversationApi} (event-mail-api.openapi.yml). Mail bodies and addresses are personal
 * data: ORGANIZER only, never cached.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class MailConversationController implements MailConversationApi {

    private final ConversationQueryService queries;
    private final ConversationService conversations;
    private final ConversationReplyService replies;
    private final ConversationAttachmentService attachmentLinks;
    private final SecurityContextHelper securityContextHelper;

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailConversationPage> listEventConversations(String eventCode, MailConversationStatus status,
            Integer page, Integer limit) {
        ConversationPage result = queries.list(eventCode, status == null ? null : status.getValue(), page, limit);
        return noStore(new MailConversationPage()
                .data(result.data().stream().map(MailConversationController::summary).toList())
                .pagination(PaginationUtils.generateMetadata(page, limit, result.total())));
    }

    /** Story 16.7: the global Mail page. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailConversationPage> listConversations(List<MailConversationStatus> status,
            String eventCode, Boolean noEvent, Integer page, Integer limit) {
        ConversationPage result = queries.listAll(status == null ? List.of()
                : status.stream().map(MailConversationStatus::getValue).toList(), eventCode, noEvent, page, limit);
        return noStore(new MailConversationPage()
                .data(result.data().stream().map(MailConversationController::summary).toList())
                .pagination(PaginationUtils.generateMetadata(page, limit, result.total())));
    }

    /** Story 16.7 AC2: the badge and the section headers. */
    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailConversationCounts> getConversationCounts() {
        ConversationQueryService.ConversationCounts counts = queries.counts();
        return noStore(new MailConversationCounts().open(counts.open()).waiting(counts.waiting())
                .noEvent(counts.noEvent()).spam(counts.spam()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailConversationDetail> getConversation(String conversationKey) {
        ConversationQueryService.ConversationDetail detail = queries.get(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
        String key = detail.conversation().conversationKey();
        return noStore(new MailConversationDetail()
                .conversation(summary(detail.conversation()))
                .messages(detail.messages().stream().map(m -> message(m, key)).toList()));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailConversationSummary> patchConversation(String conversationKey,
            MailConversationPatch patch) {
        conversations.patch(conversationKey,
                patch.getStatus() == null ? null : MailConversation.Status.valueOf(patch.getStatus().getValue()),
                patch.getAssigneeUsername(), Boolean.TRUE.equals(patch.getClearAssignee()), patch.getEventCode(),
                Boolean.TRUE.equals(patch.getClearEvent()), securityContextHelper.getCurrentUsername());
        return noStore(summary(queries.find(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey))));
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<MailMessageDetail> replyToConversation(String conversationKey,
            MailConversationReply reply) {
        String mailKey = replies.reply(conversationKey, reply.getText(), Boolean.TRUE.equals(reply.getMarkDone()),
                securityContextHelper.getCurrentUsername());
        ConversationQueryService.ConversationDetail detail = queries.get(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
        MailMessageDetail sent = detail.messages().stream()
                .filter(m -> mailKey.equals(m.mailKey()))
                .findFirst()
                .map(m -> message(m, conversationKey))
                .orElseThrow(() -> new NotFoundException("Mail", mailKey));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(sent);
    }

    @Override
    @PreAuthorize("hasRole('ORGANIZER')")
    public ResponseEntity<DownloadUrlResponse> getConversationAttachmentDownloadUrl(String conversationKey,
            String mailKey, Integer index) {
        return noStore(new DownloadUrlResponse().downloadUrl(attachmentLinks.downloadUrl(conversationKey, mailKey,
                index)));
    }

    private static MailConversationSummary summary(ConversationEntry c) {
        return new MailConversationSummary()
                .conversationKey(c.conversationKey())
                .eventCode(c.eventCode())
                .audience(MailAudience.fromValue(c.audience()))
                .subject(c.subject())
                .counterpartAddress(c.counterpartAddress())
                .counterpartName(c.counterpartName())
                .status(MailConversationStatus.fromValue(c.status()))
                .assigneeUsername(c.assigneeUsername())
                .via(c.via())
                .lastMessageAt(c.lastMessageAt().atOffset(ZoneOffset.UTC))
                .messageCount(c.messageCount())
                .mergeRequestId(c.mergeRequestId())
                .logoRequestId(c.logoRequestId());
    }

    private static MailMessageDetail message(ConversationMessage m, String conversationKey) {
        List<MailAttachment> attachments = m.attachments().stream()
                .map(a -> new MailAttachment().name(a.name()).contentType(a.contentType()).size(a.size())
                        .kept(a.storageKey() != null))
                .toList();
        return new MailMessageDetail()
                .message(new MailMessageSummary()
                        .mailKey(m.mailKey())
                        .sentAt(m.sentAt().atOffset(ZoneOffset.UTC))
                        .senderUsername(m.senderUsername())
                        .audience(MailAudience.fromValue(m.audience()))
                        .templateKey(m.templateKey())
                        .contentSource(MailMessageSummary.ContentSourceEnum.fromValue(m.contentSource()))
                        .subject(m.subject())
                        .recipientCount(m.recipientCount())
                        .deliveredCount(0)
                        .failedCount(0)
                        .messageCount(1)
                        .test(false)
                        .direction(MailMessageSummary.DirectionEnum.fromValue(m.direction()))
                        .fromAddress(m.fromAddress())
                        .fromName(m.fromName())
                        .eventCode(m.eventCode())
                        .conversationKey(conversationKey)
                        .attachments(attachments))
                .bodyHtml(m.bodyHtml())
                .recipients(List.of());
    }

    private static <T> ResponseEntity<T> noStore(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
