package ch.batbern.events.notification;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.MutableClock;
import ch.batbern.shared.test.mail.SesRecorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-mail notifications of {@link NotificationService} against real PostgreSQL, CUMS behind the
 * {@link CumsContractHarness} (the real {@code UserApiClientImpl} reads the settings) and SES as the
 * {@link SesRecorder}. Replaces the former mock-verify unit test (C6 touch rule, #1222): the
 * recipient is mailed and the row says SENT, an opted-out recipient gets neither, and a mail SES
 * refuses is recorded as FAILED. Not {@code @Transactional}: the class removes its rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("NotificationService e-mail notifications (BAT-7)")
class NotificationServiceIntegrationTest extends AbstractIntegrationTest {

    private static final String USERNAME = "notif.recipient";
    private static final String EMAIL = "success+notif-recipient@simulator.amazonses.com";

    @Autowired
    private NotificationService notificationService;
    @Autowired
    private NotificationRepository notificationRepository;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private MutableClock clock;

    @BeforeEach
    void setUp() {
        cleanUp();
        sesRecorder.reset();
    }

    @AfterEach
    void cleanUp() {
        clock.reset();
        sesRecorder.awaitAsyncSends();
        cums.reset();
        notificationRepository.deleteAll(notificationRepository.findAll().stream()
                .filter(n -> USERNAME.equals(n.getRecipientUsername()))
                .toList());
    }

    private static NotificationRequest request() {
        return NotificationRequest.builder()
                .recipientUsername(USERNAME)
                .eventCode("BATbern60")
                .type("SPEAKER_ESCALATION")
                .channel("EMAIL")
                .priority("HIGH")
                .subject("Speaker Follow-up Required")
                .body("Speaker has not responded")
                .build();
    }

    private List<Notification> rows() {
        return notificationRepository.findAll().stream()
                .filter(n -> USERNAME.equals(n.getRecipientUsername()))
                .toList();
    }

    @Test
    @DisplayName("mails the recipient and records the row as SENT")
    void should_mailRecipientAndRecordSent_when_emailNotificationsOn() {
        cums.user(USERNAME, "Nora", "Recipient", EMAIL);

        notificationService.createAndSendEmailNotification(request());

        assertThat(sesRecorder.mailsTo(EMAIL)).singleElement()
                .satisfies(mail -> assertThat(mail.subject()).isEqualTo("Speaker Follow-up Required"));
        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("SENT");
            assertThat(row.getSentAt()).isNotNull();
            assertThat(row.getChannel()).isEqualTo("EMAIL");
            assertThat(row.getPriority()).isEqualTo("HIGH");
        });
    }

    @Test
    @DisplayName("an opted-out recipient gets no mail and no row")
    void should_skipNotification_when_userOptedOut() {
        cums.userWithSettings(USERNAME, Map.of("emailNotifications", false));

        notificationService.createAndSendEmailNotification(request());

        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("a mail SES refuses is recorded as FAILED with the reason")
    void should_recordFailed_when_sesRefuses() {
        cums.user(USERNAME, "Nora", "Recipient", EMAIL);
        sesRecorder.failWith(() -> SesRecorder.sesError("MessageRejected", "AWS SES error"));

        notificationService.createAndSendEmailNotification(request());

        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("FAILED");
            assertThat(row.getFailedAt()).isNotNull();
            assertThat(row.getFailureReason()).isNotBlank();
            assertThat(row.getSentAt()).isNull();
        });
    }

    @Test
    @DisplayName("#1222: no mail and no row inside the quiet hours, read in the recipient's time zone")
    void should_skipNotification_when_insideQuietHoursOfRecipientZone() {
        // 23:30 in Auckland, 11:30 in Zurich: inside 22:00-07:00 only in the recipient's zone
        clock.setInstant(Instant.parse("2027-01-15T10:30:00Z"));
        cums.userWithSettings(USERNAME, Map.of("timezone", "Pacific/Auckland",
                "quietHoursStart", "22:00", "quietHoursEnd", "07:00"));

        notificationService.createAndSendEmailNotification(request());

        assertThat(sesRecorder.mails()).isEmpty();
        assertThat(rows()).isEmpty();
    }

    @Test
    @DisplayName("#1222: mails as usual outside the quiet hours")
    void should_mailRecipient_when_outsideQuietHours() {
        // 11:30 in Zurich: outside 22:00-07:00 (the old swapped check suppressed it)
        clock.setInstant(Instant.parse("2027-01-15T10:30:00Z"));
        cums.userWithSettings(USERNAME, Map.of("timezone", "Europe/Zurich",
                "quietHoursStart", "22:00", "quietHoursEnd", "07:00"));

        notificationService.createAndSendEmailNotification(request());

        assertThat(sesRecorder.mailsTo(USERNAME + "@test.com")).hasSize(1);
        assertThat(rows()).singleElement().satisfies(row -> {
            assertThat(row.getStatus()).isEqualTo("SENT");
            assertThat(row.getSentAt()).isEqualTo(Instant.parse("2027-01-15T10:30:00Z"));
        });
    }
}
