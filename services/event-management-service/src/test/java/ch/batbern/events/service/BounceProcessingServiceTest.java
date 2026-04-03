package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterRecipient;
import ch.batbern.events.domain.NewsletterRecipientId;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterRecipientRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for BounceProcessingService (Story 10.29 — AC5, AC10).
 */
@ExtendWith(MockitoExtension.class)
class BounceProcessingServiceTest {

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @Mock
    private NewsletterRecipientRepository recipientRepository;

    private BounceProcessingService service;

    private static final String TEST_EMAIL = "user@example.com";
    private static final UUID TEST_SEND_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new BounceProcessingService(
                subscriberRepository, recipientRepository,
                new ObjectMapper(), 3);
    }

    private NewsletterSubscriber createSubscriber(String email) {
        return NewsletterSubscriber.builder()
                .id(UUID.randomUUID())
                .email(email)
                .unsubscribeToken(UUID.randomUUID().toString())
                .bounceCount(0)
                .build();
    }

    private String buildSnsEnvelope(String notificationType, String bounceType, String email, UUID sendId) {
        String sendIdTag = sendId != null
                ? ",\"tags\":{\"sendId\":[\"" + sendId + "\"]}"
                : "";
        String innerMessage;

        if ("Bounce".equals(notificationType)) {
            innerMessage = "{\"notificationType\":\"Bounce\",\"bounce\":{\"bounceType\":\"" + bounceType
                    + "\",\"bouncedRecipients\":[{\"emailAddress\":\"" + email + "\"}]}"
                    + ",\"mail\":{\"messageId\":\"test\"" + sendIdTag + "}}";
        } else if ("Complaint".equals(notificationType)) {
            innerMessage = "{\"notificationType\":\"Complaint\",\"complaint\":{\"complainedRecipients\":"
                    + "[{\"emailAddress\":\"" + email + "\"}]}"
                    + ",\"mail\":{\"messageId\":\"test\"" + sendIdTag + "}}";
        } else {
            innerMessage = "{\"notificationType\":\"" + notificationType + "\"}";
        }

        // Escape for JSON string within JSON
        String escaped = innerMessage.replace("\"", "\\\"");
        return "{\"Type\":\"Notification\",\"Message\":\"" + escaped + "\"}";
    }

    @Test
    @DisplayName("should_suppressSubscriber_when_hardBounceReceived")
    void should_suppressSubscriber_when_hardBounceReceived() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        service.handleBounceNotification(buildSnsEnvelope("Bounce", "Permanent", TEST_EMAIL, null));

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        NewsletterSubscriber saved = captor.getValue();

        assertThat(saved.getBounceType()).isEqualTo("hard");
        assertThat(saved.getSuppressedAt()).isNotNull();
        assertThat(saved.getBounceCount()).isEqualTo(1);
        assertThat(saved.getLastBouncedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_incrementBounceCount_when_softBounceReceived")
    void should_incrementBounceCount_when_softBounceReceived() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        service.handleBounceNotification(buildSnsEnvelope("Bounce", "Transient", TEST_EMAIL, null));

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        NewsletterSubscriber saved = captor.getValue();

        assertThat(saved.getBounceType()).isEqualTo("soft");
        assertThat(saved.getBounceCount()).isEqualTo(1);
        assertThat(saved.getSuppressedAt()).isNull(); // Not suppressed yet (threshold = 3)
    }

    @Test
    @DisplayName("should_suppressSubscriber_when_softBounceCountReachesThree")
    void should_suppressSubscriber_when_softBounceCountReachesThree() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        sub.setBounceCount(2); // Already 2 bounces
        sub.setBounceType("soft");
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        service.handleBounceNotification(buildSnsEnvelope("Bounce", "Transient", TEST_EMAIL, null));

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        NewsletterSubscriber saved = captor.getValue();

        assertThat(saved.getBounceCount()).isEqualTo(3);
        assertThat(saved.getSuppressedAt()).isNotNull(); // Threshold reached
    }

    @Test
    @DisplayName("should_suppressSubscriber_when_complaintReceived")
    void should_suppressSubscriber_when_complaintReceived() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        service.handleBounceNotification(buildSnsEnvelope("Complaint", null, TEST_EMAIL, null));

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        NewsletterSubscriber saved = captor.getValue();

        assertThat(saved.getBounceType()).isEqualTo("complaint");
        assertThat(saved.getSuppressedAt()).isNotNull();
    }

    @Test
    @DisplayName("should_updateRecipient_when_sendIdInMessageTags")
    void should_updateRecipient_when_sendIdInMessageTags() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        NewsletterRecipient recipient = NewsletterRecipient.builder()
                .id(new NewsletterRecipientId(TEST_SEND_ID, TEST_EMAIL))
                .deliveryStatus("sent")
                .build();
        when(recipientRepository.findById(any())).thenReturn(Optional.of(recipient));

        service.handleBounceNotification(buildSnsEnvelope("Bounce", "Permanent", TEST_EMAIL, TEST_SEND_ID));

        verify(recipientRepository).save(argThat(r ->
                "hard".equals(r.getBounceType()) && r.getBouncedAt() != null));
    }

    @Test
    @DisplayName("should_beNaturallyIdempotent_when_sameBounceProcessedTwice")
    void should_beNaturallyIdempotent_when_sameBounceProcessedTwice() {
        NewsletterSubscriber sub = createSubscriber(TEST_EMAIL);
        Instant originalSuppression = Instant.parse("2026-01-01T00:00:00Z");
        sub.setSuppressedAt(originalSuppression); // Already suppressed
        sub.setBounceType("hard");
        sub.setBounceCount(1);
        when(subscriberRepository.findByEmail(TEST_EMAIL)).thenReturn(Optional.of(sub));

        service.handleBounceNotification(buildSnsEnvelope("Bounce", "Permanent", TEST_EMAIL, null));

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        NewsletterSubscriber saved = captor.getValue();

        // COALESCE: original suppression timestamp preserved
        assertThat(saved.getSuppressedAt()).isEqualTo(originalSuppression);
        // Bounce count incremented
        assertThat(saved.getBounceCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("should_ignoreUnknownNotificationType")
    void should_ignoreUnknownNotificationType() {
        service.handleBounceNotification(buildSnsEnvelope("Delivery", null, TEST_EMAIL, null));

        verifyNoInteractions(subscriberRepository);
        verifyNoInteractions(recipientRepository);
    }
}
