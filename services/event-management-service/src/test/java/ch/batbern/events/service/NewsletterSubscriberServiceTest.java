package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.dto.NewsletterSubscriptionStatusResponse;
import ch.batbern.events.exception.DuplicateSubscriberException;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NewsletterSubscriberService (Story 10.7 — AC1, AC2, AC3, AC6, AC7, AC12).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterSubscriberService Unit Tests")
class NewsletterSubscriberServiceTest {

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @InjectMocks
    private NewsletterSubscriberService service;

    private NewsletterSubscriber activeSubscriber;
    private NewsletterSubscriber unsubscribedSubscriber;

    @BeforeEach
    void setUp() {
        activeSubscriber = NewsletterSubscriber.builder()
                .email("active@example.com")
                .firstName("Alice")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-active")
                .build();

        unsubscribedSubscriber = NewsletterSubscriber.builder()
                .email("unsubscribed@example.com")
                .firstName("Bob")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-unsubscribed")
                .unsubscribedAt(Instant.now().minusSeconds(3600))
                .build();
    }

    // ── subscribe ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("subscribe: new email → creates subscriber with unique token")
    void subscribe_newEmail_createsSubscriber() {
        when(subscriberRepository.findByEmail("new@example.com")).thenReturn(Optional.empty());
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NewsletterSubscriber result = service.subscribe("new@example.com", "Test", "en", "explicit", null);

        assertThat(result.getEmail()).isEqualTo("new@example.com");
        assertThat(result.getUnsubscribeToken()).isNotBlank();
        assertThat(result.getUnsubscribedAt()).isNull();
    }

    @Test
    @DisplayName("subscribe: already active email → throws DuplicateSubscriberException (409)")
    void subscribe_alreadyActive_throws409() {
        when(subscriberRepository.findByEmail("active@example.com")).thenReturn(Optional.of(activeSubscriber));

        assertThatThrownBy(() -> service.subscribe("active@example.com", null, null, "explicit", null))
                .isInstanceOf(DuplicateSubscriberException.class);
    }

    @Test
    @DisplayName("subscribe: previously unsubscribed email → reactivates and preserves token")
    void subscribe_previouslyUnsubscribed_reactivates() {
        String originalToken = unsubscribedSubscriber.getUnsubscribeToken();
        when(subscriberRepository.findByEmail("unsubscribed@example.com")).thenReturn(Optional.of(unsubscribedSubscriber));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        NewsletterSubscriber result = service.subscribe("unsubscribed@example.com", null, null, "explicit", null);

        assertThat(result.getUnsubscribedAt()).isNull();
        assertThat(result.getUnsubscribeToken()).isEqualTo(originalToken);
    }

    // ── verifyToken ───────────────────────────────────────────────────────────

    @Test
    @DisplayName("verifyToken: valid token for active subscriber → returns email")
    void verifyToken_validToken_returnsEmail() {
        when(subscriberRepository.findByUnsubscribeToken("token-active")).thenReturn(Optional.of(activeSubscriber));

        Optional<String> email = service.verifyToken("token-active");

        assertThat(email).contains("active@example.com");
    }

    @Test
    @DisplayName("verifyToken: token for already-unsubscribed subscriber → returns empty")
    void verifyToken_unsubscribedToken_returnsEmpty() {
        when(subscriberRepository.findByUnsubscribeToken("token-unsubscribed")).thenReturn(Optional.of(unsubscribedSubscriber));

        Optional<String> email = service.verifyToken("token-unsubscribed");

        assertThat(email).isEmpty();
    }

    @Test
    @DisplayName("verifyToken: unknown token → returns empty")
    void verifyToken_unknownToken_returnsEmpty() {
        when(subscriberRepository.findByUnsubscribeToken("bad-token")).thenReturn(Optional.empty());

        Optional<String> email = service.verifyToken("bad-token");

        assertThat(email).isEmpty();
    }

    // ── unsubscribeByToken ────────────────────────────────────────────────────

    @Test
    @DisplayName("unsubscribeByToken: valid token → sets unsubscribedAt")
    void unsubscribeByToken_valid_setsUnsubscribedAt() {
        when(subscriberRepository.findByUnsubscribeToken("token-active")).thenReturn(Optional.of(activeSubscriber));
        when(subscriberRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.unsubscribeByToken("token-active");

        ArgumentCaptor<NewsletterSubscriber> captor = ArgumentCaptor.forClass(NewsletterSubscriber.class);
        verify(subscriberRepository).save(captor.capture());
        assertThat(captor.getValue().getUnsubscribedAt()).isNotNull();
    }

    @Test
    @DisplayName("unsubscribeByToken: unknown token → throws NoSuchElementException")
    void unsubscribeByToken_unknownToken_throwsNotFound() {
        when(subscriberRepository.findByUnsubscribeToken("bad")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.unsubscribeByToken("bad"))
                .isInstanceOf(NoSuchElementException.class);
    }

    // ── getMySubscription ─────────────────────────────────────────────────────

    @Test
    @DisplayName("getMySubscription: found by username → returns subscribed=true")
    void getMySubscription_byUsername_returnsActive() {
        when(subscriberRepository.findByUsername("alice")).thenReturn(Optional.of(activeSubscriber));

        NewsletterSubscriptionStatusResponse resp = service.getMySubscription("alice", "active@example.com");

        assertThat(resp.isSubscribed()).isTrue();
        assertThat(resp.getEmail()).isEqualTo("active@example.com");
    }

    @Test
    @DisplayName("getMySubscription: not found → returns subscribed=false")
    void getMySubscription_notFound_returnsNotSubscribed() {
        when(subscriberRepository.findByUsername("unknown")).thenReturn(Optional.empty());
        when(subscriberRepository.findByEmail("unknown@example.com")).thenReturn(Optional.empty());

        NewsletterSubscriptionStatusResponse resp = service.getMySubscription("unknown", "unknown@example.com");

        assertThat(resp.isSubscribed()).isFalse();
    }

    // ── getActiveCount ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getActiveCount: delegates to repository count")
    void getActiveCount_delegatesToRepository() {
        when(subscriberRepository.countByUnsubscribedAtIsNull()).thenReturn(42L);

        assertThat(service.getActiveCount()).isEqualTo(42L);
    }

    // ── findActiveSubscribers ─────────────────────────────────────────────────

    @Test
    @DisplayName("findActiveSubscribers: returns only active records")
    void findActiveSubscribers_returnsActive() {
        when(subscriberRepository.findByUnsubscribedAtIsNull()).thenReturn(List.of(activeSubscriber));

        List<NewsletterSubscriber> result = service.findActiveSubscribers();

        assertThat(result).containsExactly(activeSubscriber);
    }
}
