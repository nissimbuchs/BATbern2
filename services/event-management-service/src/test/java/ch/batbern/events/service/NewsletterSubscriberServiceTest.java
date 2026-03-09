package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NewsletterSubscriberService.unsubscribeByEmail() (Story 10.17 — AC9).
 */
@ExtendWith(MockitoExtension.class)
class NewsletterSubscriberServiceTest {

    @Mock
    private NewsletterSubscriberRepository subscriberRepository;

    @InjectMocks
    private NewsletterSubscriberService newsletterSubscriberService;

    private NewsletterSubscriber activeSubscriber;
    private NewsletterSubscriber alreadyUnsubscribedSubscriber;

    @BeforeEach
    void setUp() {
        activeSubscriber = NewsletterSubscriber.builder()
                .email("active@example.com")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-abc")
                .build();

        alreadyUnsubscribedSubscriber = NewsletterSubscriber.builder()
                .email("gone@example.com")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-xyz")
                .build();
        alreadyUnsubscribedSubscriber.setUnsubscribedAt(Instant.now().minusSeconds(3600));
    }

    @Test
    void unsubscribeByEmail_withKnownActiveEmail_setsUnsubscribedAt() {
        when(subscriberRepository.findByEmail("active@example.com"))
                .thenReturn(Optional.of(activeSubscriber));
        when(subscriberRepository.save(any(NewsletterSubscriber.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        newsletterSubscriberService.unsubscribeByEmail("active@example.com");

        assertThat(activeSubscriber.getUnsubscribedAt()).isNotNull();
        verify(subscriberRepository).save(activeSubscriber);
    }

    @Test
    void unsubscribeByEmail_withUnknownEmail_silentNoOp() {
        when(subscriberRepository.findByEmail("unknown@example.com"))
                .thenReturn(Optional.empty());

        newsletterSubscriberService.unsubscribeByEmail("unknown@example.com");

        verify(subscriberRepository, never()).save(any());
    }

    @Test
    void unsubscribeByEmail_withAlreadyUnsubscribedEmail_silentNoOp() {
        when(subscriberRepository.findByEmail("gone@example.com"))
                .thenReturn(Optional.of(alreadyUnsubscribedSubscriber));

        Instant originalUnsubscribedAt = alreadyUnsubscribedSubscriber.getUnsubscribedAt();

        newsletterSubscriberService.unsubscribeByEmail("gone@example.com");

        assertThat(alreadyUnsubscribedSubscriber.getUnsubscribedAt()).isEqualTo(originalUnsubscribedAt);
        verify(subscriberRepository, never()).save(any());
    }
}
