package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSend;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSendRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;

/**
 * #1129: the newsletter send loop and its retry, against real PostgreSQL (Testcontainers).
 *
 * <p>{@link EmailService} is a Mockito mock, so no mail leaves the test. The send and retry
 * cores are driven on the unproxied bean, i.e. synchronously in the test thread and inside the
 * test transaction (rolled back afterwards), so a subscriber can unsubscribe "mid-send" from
 * inside the mocked mail call, exactly as a recipient clicking the link of the mail they just got.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Newsletter send iteration + retry (#1129)")
class NewsletterSendIterationIntegrationTest extends AbstractIntegrationTest {

    private static final int SUBSCRIBERS = 130; // three pages of 50

    @Autowired
    private NewsletterEmailService newsletterEmailService;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private NewsletterSendRepository sendRepository;
    @Autowired
    private NewsletterRecipientRepository recipientRepository;
    @Autowired
    private EventRepository eventRepository;

    @MockitoBean
    private UserApiClient userApiClient;
    @MockitoBean
    private EmailService emailService;

    private NewsletterEmailService target;
    private Object originalRateDelay;
    private final List<String> mailedTo = new CopyOnWriteArrayList<>();
    private final List<Map<String, String>> renderedVariables = new CopyOnWriteArrayList<>();
    private Consumer<String> onMail = email -> { };

    @BeforeEach
    void setUp() {
        recipientRepository.deleteAll();
        sendRepository.deleteAll();
        subscriberRepository.deleteAll();

        target = AopTestUtils.getUltimateTargetObject(newsletterEmailService);
        originalRateDelay = ReflectionTestUtils.getField(target, "sendRateDelayMs");
        ReflectionTestUtils.setField(target, "sendRateDelayMs", 0L);

        lenient().when(emailService.replaceVariables(anyString(), anyMap())).thenAnswer(inv -> {
            renderedVariables.add(Map.copyOf(inv.<Map<String, String>>getArgument(1)));
            return inv.getArgument(0);
        });
        lenient().doAnswer(inv -> recordMail(inv.getArgument(1)))
                .when(emailService).sendHtmlEmailSync(any(), anyString(), any(), any(), any(), any(), any());
        lenient().doAnswer(inv -> recordMail(inv.getArgument(1)))
                .when(emailService).sendHtmlEmailSyncWithAttachments(any(), anyString(), any(), any(), any(), any());
    }

    @AfterEach
    void restoreRateDelay() {
        ReflectionTestUtils.setField(target, "sendRateDelayMs", originalRateDelay);
    }

    private Object recordMail(String email) {
        mailedTo.add(email);
        onMail.accept(email);
        return null;
    }

    // ── (b) send loop: a page-1 subscriber unsubscribes mid-send ──────────────

    @Test
    @DisplayName("should_contactEveryActiveSubscriberExactlyOnce_when_pageOneSubscriberUnsubscribesMidSend")
    void should_contactEveryActiveSubscriberExactlyOnce_when_pageOneSubscriberUnsubscribesMidSend() {
        Event event = saveEvent();
        List<String> all = new ArrayList<>();
        for (int i = 0; i < SUBSCRIBERS; i++) {
            all.add(saveSubscriber(String.format("reader%03d@example.com", i), null, null).getEmail());
        }
        NewsletterSend send = savePendingSend(event.getId(), SUBSCRIBERS);

        // The tenth mail goes out while the first recipient (already mailed, on page 1) clicks
        // "unsubscribe": the active set shrinks by one under the running send.
        onMail = email -> {
            if (mailedTo.size() == 10) {
                NewsletterSubscriber first = subscriberRepository.findByEmailIgnoreCase(mailedTo.get(0)).orElseThrow();
                first.setUnsubscribedAt(Instant.now());
                subscriberRepository.saveAndFlush(first);
            }
        };

        target.executeNewsletterSendAsync(send.getId(), event, false, "de", "newsletter-event");

        assertThat(mailedTo).doesNotHaveDuplicates();
        assertThat(new HashSet<>(mailedTo)).isEqualTo(new HashSet<>(all));
        NewsletterSend done = sendRepository.findById(send.getId()).orElseThrow();
        assertThat(done.getSentCount()).isEqualTo(SUBSCRIBERS);
        assertThat(done.getStatus()).isEqualTo(NewsletterEmailService.STATUS_COMPLETED);
    }

    @Test
    @DisplayName("should_neverMailSubscriber_when_theyUnsubscribeBeforeTheSendReachesThem")
    void should_neverMailSubscriber_when_theyUnsubscribeBeforeTheSendReachesThem() {
        Event event = saveEvent();
        List<NewsletterSubscriber> subs = new ArrayList<>();
        for (int i = 0; i < SUBSCRIBERS; i++) {
            subs.add(saveSubscriber(String.format("later%03d@example.com", i), null, null));
        }
        NewsletterSend send = savePendingSend(event.getId(), SUBSCRIBERS);
        Set<String> leavers = new HashSet<>();

        // After the first mail, every not-yet-mailed subscriber with an odd index leaves.
        onMail = email -> {
            if (mailedTo.size() == 1) {
                for (int i = 1; i < subs.size(); i += 2) {
                    NewsletterSubscriber s = subs.get(i);
                    if (!s.getEmail().equals(email)) {
                        s.setUnsubscribedAt(Instant.now());
                        subscriberRepository.save(s);
                        leavers.add(s.getEmail());
                    }
                }
                subscriberRepository.flush();
            }
        };

        target.executeNewsletterSendAsync(send.getId(), event, false, "de", "newsletter-event");

        Set<String> expected = new HashSet<>();
        subs.forEach(s -> expected.add(s.getEmail()));
        expected.removeAll(leavers);
        assertThat(mailedTo).doesNotHaveDuplicates();
        assertThat(new HashSet<>(mailedTo)).isEqualTo(expected);
    }

    // ── (c) retry: failed rows of gone subscribers are skipped ─────────────────

    @Test
    @DisplayName("should_skipFailedRecipients_when_subscriberDeletedUnsubscribedOrSuppressed")
    void should_skipFailedRecipients_when_subscriberDeletedUnsubscribedOrSuppressed() {
        Event event = saveEvent();
        NewsletterSubscriber stillActive = saveSubscriber("Active@Example.com", null, null);
        saveSubscriber("left@example.com", Instant.now(), null);
        saveSubscriber("bounced@example.com", null, Instant.now());
        NewsletterSend send = savePendingSend(event.getId(), 4);
        send.setStatus(NewsletterEmailService.STATUS_PARTIAL);
        send.setSentCount(0);
        send.setFailedCount(4);
        sendRepository.save(send);

        recordRecipient(send.getId(), "active@example.com", "failed"); // case differs from the subscriber row
        recordRecipient(send.getId(), "left@example.com", "failed");
        recordRecipient(send.getId(), "bounced@example.com", "failed");
        recordRecipient(send.getId(), "deleted-bot@example.com", "failed"); // subscriber row deleted since
        recipientRepository.flush();

        target.executeRetryAsync(send.getId(), event, false, "de", "newsletter-event");

        assertThat(mailedTo).containsExactly("active@example.com");
        // Per-recipient renders carry the subscriber's own token, never an empty one. (The subject
        // is rendered once from the base variables, whose unsubscribeLink is blank by design.)
        assertThat(renderedVariables)
                .extracting(vars -> vars.getOrDefault("unsubscribeLink", ""))
                .filteredOn(link -> !link.isEmpty())
                .isNotEmpty()
                .allSatisfy(link -> assertThat(link).endsWith("token=" + stillActive.getUnsubscribeToken()));

        assertThat(status(send.getId(), "active@example.com")).isEqualTo("sent");
        assertThat(status(send.getId(), "left@example.com")).isEqualTo("skipped");
        assertThat(status(send.getId(), "bounced@example.com")).isEqualTo("skipped");
        assertThat(status(send.getId(), "deleted-bot@example.com")).isEqualTo("skipped");

        NewsletterSend done = sendRepository.findById(send.getId()).orElseThrow();
        assertThat(done.getStatus()).isEqualTo(NewsletterEmailService.STATUS_COMPLETED);
        assertThat(done.getFailedCount()).isZero();
        assertThat(done.getSentCount()).isEqualTo(1);
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private String status(UUID sendId, String email) {
        return recipientRepository.findById(new NewsletterRecipientId(sendId, email))
                .map(NewsletterRecipient::getDeliveryStatus).orElse("<none>");
    }

    private void recordRecipient(UUID sendId, String email, String deliveryStatus) {
        recipientRepository.save(NewsletterRecipient.builder()
                .id(new NewsletterRecipientId(sendId, email))
                .deliveryStatus(deliveryStatus)
                .build());
    }

    private NewsletterSubscriber saveSubscriber(String email, Instant unsubscribedAt, Instant suppressedAt) {
        return subscriberRepository.save(NewsletterSubscriber.builder()
                .email(email)
                .language("de")
                .source("explicit")
                .unsubscribeToken("tok-" + UUID.randomUUID())
                .unsubscribedAt(unsubscribedAt)
                .suppressedAt(suppressedAt)
                .build());
    }

    private NewsletterSend savePendingSend(UUID eventId, int recipientCount) {
        return sendRepository.save(NewsletterSend.builder()
                .eventId(eventId)
                .status(NewsletterEmailService.STATUS_PENDING)
                .recipientCount(recipientCount)
                .sentCount(0)
                .failedCount(0)
                .locale("de")
                .sentByUsername("organizer")
                .sentAt(Instant.now())
                .templateKey("newsletter-event")
                .build());
    }

    private Event saveEvent() {
        Instant date = Instant.now().plus(30, ChronoUnit.DAYS);
        return eventRepository.save(Event.builder()
                .eventCode("BATbern1129")
                .eventNumber(1129)
                .title("Keyset Newsletter Event")
                .date(date)
                .registrationDeadline(date.minus(10, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Test Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.core.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .organizerUsername("org.user")
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }
}
