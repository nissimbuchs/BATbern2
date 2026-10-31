package ch.batbern.events.mailcenter;

import ch.batbern.events.domain.MailRecipient.Status;
import ch.batbern.events.repository.MailRecipientRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Story 16.1b: sets {@code mail_recipient.status} from the SES events of the transactional
 * configuration set (SNS envelope, as delivered to SQS by {@link MailDeliveryStatusListener}).
 *
 * <ul>
 *   <li>Delivery: the listed recipients become DELIVERED.</li>
 *   <li>Bounce: the bounced recipients become FAILED, reason {@code bounceType/bounceSubType}.</li>
 *   <li>Complaint: the complaining recipients become FAILED, reason {@code complaint}.</li>
 *   <li>Reject: SES refused the whole message, so every recipient becomes FAILED with the reason.</li>
 * </ul>
 *
 * <p>Matching is by SES Message-ID and address (case-insensitive). Statuses only move forward
 * ({@link DeliveryStatusRule}). Unknown Message-IDs are counted and ignored (AC3). An unreadable
 * message throws, so SQS retries it and moves it to the DLQ after 3 attempts (AC5).
 *
 * <p>Separate from {@code BounceProcessingService} on purpose: transactional bounces never touch
 * newsletter suppression (AC4).
 */
@Service
@Slf4j
public class MailDeliveryStatusService {

    private static final int MAX_REASON_LENGTH = 500;
    private static final Outcome NOTHING_TO_MATCH = new Outcome(true, 0);

    private final MailRecipientRepository recipients;
    private final ObjectMapper objectMapper;
    private final MailCenterMetrics metrics;

    public MailDeliveryStatusService(MailRecipientRepository recipients, ObjectMapper objectMapper,
            MailCenterMetrics metrics) {
        this.recipients = recipients;
        this.objectMapper = objectMapper;
        this.metrics = metrics;
    }

    /**
     * Result of one SQS message. {@code known} is false only when a delivery, bounce, complaint or
     * reject names a Message-ID with no recorded recipient.
     */
    public record Outcome(boolean known, int updated) {
    }

    /** The SQS message is not an SNS envelope around an SES event. */
    public static class UnreadableEventException extends RuntimeException {
        UnreadableEventException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Transactional
    public Outcome process(String snsMessageBody) {
        JsonNode envelope = read(snsMessageBody);
        String type = envelope.path("Type").asText("");
        if (!"Notification".equals(type)) {
            log.debug("Ignoring SNS message of type {}", type);
            return NOTHING_TO_MATCH;
        }
        JsonNode event = read(envelope.path("Message").asText(""));
        String eventType = event.path("eventType").asText(event.path("notificationType").asText(""));
        String sesMessageId = event.path("mail").path("messageId").asText("");
        if (sesMessageId.isBlank()) {
            throw new UnreadableEventException("SES event without mail.messageId", null);
        }

        return switch (eventType) {
            case "Delivery" -> apply(sesMessageId, addresses(event.path("delivery").path("recipients"), null),
                    Status.DELIVERED, null);
            case "Bounce" -> apply(sesMessageId,
                    addresses(event.path("bounce").path("bouncedRecipients"), "emailAddress"),
                    Status.FAILED, bounceReason(event.path("bounce")));
            case "Complaint" -> apply(sesMessageId,
                    addresses(event.path("complaint").path("complainedRecipients"), "emailAddress"),
                    Status.FAILED, "complaint");
            case "Reject" -> apply(sesMessageId, null, Status.FAILED,
                    event.path("reject").path("reason").asText("rejected"));
            default -> {
                log.debug("Ignoring SES event type {} for {}", eventType, sesMessageId);
                yield NOTHING_TO_MATCH;
            }
        };
    }

    /** {@code addresses == null} means every recipient of the message (reject). */
    private Outcome apply(String sesMessageId, List<String> addresses, Status target, String reason) {
        if (!recipients.existsBySesMessageId(sesMessageId)) {
            log.debug("SES event for unrecorded Message-ID {} ignored", sesMessageId);
            metrics.increment(MailCenterMetrics.DELIVERY_EVENTS_UNMATCHED);
            return new Outcome(false, 0);
        }
        String storedReason = reason == null || reason.length() <= MAX_REASON_LENGTH
                ? reason : reason.substring(0, MAX_REASON_LENGTH);
        Instant now = Instant.now();
        int updated = 0;
        if (addresses == null) {
            updated = recipients.updateStatusForMessage(sesMessageId, target, storedReason, now,
                    DeliveryStatusRule.sourcesFor(target));
        } else {
            for (String address : addresses) {
                updated += recipients.updateStatus(sesMessageId, address, target, storedReason, now,
                        DeliveryStatusRule.sourcesFor(target));
            }
        }
        log.info("SES {} for {}: {} recipient(s) updated", target, sesMessageId, updated);
        return new Outcome(true, updated);
    }

    private static List<String> addresses(JsonNode list, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode item : list) {
            String address = (field == null ? item : item.path(field)).asText("");
            if (!address.isBlank()) {
                result.add(address);
            }
        }
        return result;
    }

    private static String bounceReason(JsonNode bounce) {
        String type = bounce.path("bounceType").asText("Undetermined");
        String subType = bounce.path("bounceSubType").asText("");
        return subType.isBlank() ? type : type + "/" + subType;
    }

    private JsonNode read(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            throw new UnreadableEventException("Not a JSON SES/SNS message", e);
        }
    }
}
