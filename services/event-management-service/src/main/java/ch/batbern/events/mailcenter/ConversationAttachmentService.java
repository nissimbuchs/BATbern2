package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.mailcenter.inbound.InboundAttachmentStore;
import ch.batbern.shared.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Story 16.6: a short-lived link to an inbound attachment kept by {@link InboundAttachmentStore}.
 * The file stays private in the inbound bucket; the link downloads it under its original name.
 */
@Service
@RequiredArgsConstructor
public class ConversationAttachmentService {

    static final Duration LINK_VALIDITY = Duration.ofMinutes(5);

    private final ConversationQueryService queries;
    private final InboundAttachmentStore store;
    private final S3Presigner presigner;

    /**
     * Presigned download URL, valid {@link #LINK_VALIDITY}, built like
     * {@code SessionMaterialsService.generateDownloadUrl} (injected {@link S3Presigner}).
     *
     * @throws NotFoundException no such message in the conversation, or the file was not kept
     */
    public String downloadUrl(String conversationKey, String mailKey, int index) {
        ConversationQueryService.ConversationDetail detail = queries.get(conversationKey)
                .orElseThrow(() -> new NotFoundException("Conversation", conversationKey));
        List<MailMessage.Attachment> attachments = detail.messages().stream()
                .filter(m -> m.mailKey().equals(mailKey))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("Mail", mailKey))
                .attachments();
        if (!store.enabled() || index < 0 || index >= attachments.size()
                || attachments.get(index).storageKey() == null) {
            throw new NotFoundException("Attachment", mailKey + "/" + index);
        }
        MailMessage.Attachment attachment = attachments.get(index);
        String filename = URLEncoder.encode(attachment.name(), StandardCharsets.UTF_8).replace("+", "%20");
        return presigner.presignGetObject(GetObjectPresignRequest.builder()
                .signatureDuration(LINK_VALIDITY)
                .getObjectRequest(GetObjectRequest.builder()
                        .bucket(store.bucket())
                        .key(attachment.storageKey())
                        .responseContentDisposition("attachment; filename*=UTF-8''" + filename)
                        .build())
                .build()).url().toString();
    }
}
