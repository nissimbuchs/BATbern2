package ch.batbern.events.mailcenter;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.MailMessage;
import ch.batbern.events.domain.MailRecipient;
import ch.batbern.events.domain.MailRecipient.Status;
import ch.batbern.events.repository.MailMessageRepository;
import ch.batbern.events.repository.MailRecipientRepository;
import ch.batbern.shared.mail.MailContext;
import ch.batbern.shared.mail.OutboundMail;
import ch.batbern.shared.mail.OutboundMailRecorder;
import ch.batbern.shared.test.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Story 16.1b against real PostgreSQL: SES events of the transactional configuration set, as SNS
 * delivers them to SQS, set {@code mail_recipient.status}. Synthetic SES simulator addresses only.
 *
 * <p>Not {@code @Transactional}: the recorder commits in its own transaction, so this class removes
 * its own rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Delivery status from SES events (PostgreSQL)")
class MailDeliveryStatusIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern914";
    private static final String TO = "success+speaker@simulator.amazonses.com";
    private static final String CC = "Bounce+Speaker.Private@simulator.amazonses.com";

    @Autowired
    private OutboundMailRecorder recorder;
    @Autowired
    private MailDeliveryStatusService deliveryStatus;
    @Autowired
    private MailMessageRepository messages;
    @Autowired
    private MailRecipientRepository recipients;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private ObjectMapper objectMapper;

    private String sesMessageId;
    private MailMessage message;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesMessageId = "0107019a-" + UUID.randomUUID() + "-000000";
        MailContext context = MailContext.individual(EVENT_CODE, MailContext.Audience.SPEAKERS, "speaker-invitation");
        recorder.record(context, new OutboundMail(TO, List.of(CC), "Invitation", "<p>b</p>", sesMessageId, null));
        message = messages.findBySendGroupId(context.sendGroupId()).orElseThrow();
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
    }

    @Test
    @DisplayName("AC1: a delivery event marks the listed recipients DELIVERED, matched case-insensitively")
    void delivery_marksDelivered() {
        MailDeliveryStatusService.Outcome outcome =
                deliveryStatus.process(sns(delivery(sesMessageId, TO.toUpperCase(), CC.toLowerCase())));

        assertThat(outcome.known()).isTrue();
        assertThat(outcome.updated()).isEqualTo(2);
        assertThat(statuses()).containsEntry(TO, Status.DELIVERED).containsEntry(CC, Status.DELIVERED);
    }

    @Test
    @DisplayName("AC2: a bounce marks only the bounced recipient FAILED with type/subtype")
    void bounce_marksFailedWithReason() {
        deliveryStatus.process(sns(bounce(sesMessageId, CC, "Permanent", "NoEmail")));

        assertThat(statuses()).containsEntry(TO, Status.SENT).containsEntry(CC, Status.FAILED);
        assertThat(reasons()).containsEntry(CC, "Permanent/NoEmail");
    }

    @Test
    @DisplayName("AC2: a complaint marks the recipient FAILED with reason complaint")
    void complaint_marksFailed() {
        deliveryStatus.process(sns(delivery(sesMessageId, TO)));
        deliveryStatus.process(sns(complaint(sesMessageId, TO)));

        assertThat(statuses()).containsEntry(TO, Status.FAILED);
        assertThat(reasons()).containsEntry(TO, "complaint");
    }

    @Test
    @DisplayName("AC2: a reject has no recipient list and fails every recipient of the message")
    void reject_failsAllRecipients() {
        deliveryStatus.process(sns(reject(sesMessageId, "Bad content")));

        assertThat(statuses()).containsEntry(TO, Status.FAILED).containsEntry(CC, Status.FAILED);
        assertThat(reasons()).containsEntry(TO, "Bad content").containsEntry(CC, "Bad content");
    }

    @Test
    @DisplayName("AC6: a DELIVERED arriving after a bounce does not overwrite FAILED")
    void failed_notOverwrittenByLaterDelivery() {
        deliveryStatus.process(sns(bounce(sesMessageId, CC, "Transient", "MailboxFull")));
        MailDeliveryStatusService.Outcome outcome = deliveryStatus.process(sns(delivery(sesMessageId, TO, CC)));

        assertThat(outcome.updated()).isEqualTo(1);
        assertThat(statuses()).containsEntry(TO, Status.DELIVERED).containsEntry(CC, Status.FAILED);
        assertThat(reasons()).containsEntry(CC, "Transient/MailboxFull");
    }

    @Test
    @DisplayName("AC3: an unknown Message-ID is ignored without error and reported as unknown")
    void unknownMessageId_ignored() {
        MailDeliveryStatusService.Outcome outcome =
                deliveryStatus.process(sns(delivery("0107019a-not-recorded-000000", TO)));

        assertThat(outcome.known()).isFalse();
        assertThat(outcome.updated()).isZero();
        assertThat(statuses()).containsEntry(TO, Status.SENT).containsEntry(CC, Status.SENT);
    }

    @Test
    @DisplayName("event kinds outside delivery/bounce/complaint/reject and non-notifications are ignored")
    void otherMessages_ignored() {
        ObjectNode open = event("Open", sesMessageId);
        assertThat(deliveryStatus.process(sns(open)).updated()).isZero();

        ObjectNode confirmation = objectMapper.createObjectNode().put("Type", "SubscriptionConfirmation");
        assertThat(deliveryStatus.process(confirmation.toString()).updated()).isZero();
        assertThat(statuses()).containsEntry(TO, Status.SENT);
    }

    @Test
    @DisplayName("AC5: a malformed message throws, so SQS retries it and then moves it to the DLQ")
    void malformed_throws() {
        assertThatThrownBy(() -> deliveryStatus.process("{\"Type\":\"Notification\",\"Message\":\"not json"))
                .isInstanceOf(MailDeliveryStatusService.UnreadableEventException.class);
    }

    private Map<String, Status> statuses() {
        return rows().stream().collect(Collectors.toMap(MailRecipient::getAddress, MailRecipient::getStatus));
    }

    private Map<String, String> reasons() {
        return rows().stream().filter(r -> r.getFailureReason() != null)
                .collect(Collectors.toMap(MailRecipient::getAddress, MailRecipient::getFailureReason));
    }

    private List<MailRecipient> rows() {
        return recipients.findByMailMessageId(message.getId());
    }

    // --- SES event JSON as delivered by a configuration-set SNS destination ---

    private String sns(ObjectNode sesEvent) {
        ObjectNode envelope = objectMapper.createObjectNode();
        envelope.put("Type", "Notification");
        envelope.put("MessageId", UUID.randomUUID().toString());
        envelope.put("TopicArn", "arn:aws:sns:eu-central-1:123456789012:batbern-test-ses-transactional-events");
        envelope.put("Message", sesEvent.toString());
        return envelope.toString();
    }

    private ObjectNode event(String eventType, String messageId) {
        ObjectNode event = objectMapper.createObjectNode();
        event.put("eventType", eventType);
        ObjectNode mail = event.putObject("mail");
        mail.put("messageId", messageId);
        mail.put("source", "noreply@batbern.ch");
        mail.putArray("destination").add(TO).add(CC);
        return event;
    }

    private ObjectNode delivery(String messageId, String... addresses) {
        ObjectNode event = event("Delivery", messageId);
        ArrayNode list = event.putObject("delivery").putArray("recipients");
        for (String address : addresses) {
            list.add(address);
        }
        return event;
    }

    private ObjectNode bounce(String messageId, String address, String type, String subType) {
        ObjectNode event = event("Bounce", messageId);
        ObjectNode bounce = event.putObject("bounce");
        bounce.put("bounceType", type);
        bounce.put("bounceSubType", subType);
        bounce.putArray("bouncedRecipients").addObject()
                .put("emailAddress", address).put("diagnosticCode", "smtp; 550 5.1.1 user unknown");
        return event;
    }

    private ObjectNode complaint(String messageId, String address) {
        ObjectNode event = event("Complaint", messageId);
        event.putObject("complaint").putArray("complainedRecipients").addObject().put("emailAddress", address);
        return event;
    }

    private ObjectNode reject(String messageId, String reason) {
        ObjectNode event = event("Reject", messageId);
        event.putObject("reject").put("reason", reason);
        return event;
    }
}
