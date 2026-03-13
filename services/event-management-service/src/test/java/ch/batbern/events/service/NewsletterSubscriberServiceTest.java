package ch.batbern.events.service;

import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.shared.api.PaginationParams;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for NewsletterSubscriberService (Stories 10.17 + 10.28).
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NewsletterSubscriberService Unit Tests")
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
                .id(UUID.randomUUID())
                .email("active@example.com")
                .firstName("Alice")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-abc")
                .subscribedAt(Instant.now())
                .build();

        alreadyUnsubscribedSubscriber = NewsletterSubscriber.builder()
                .id(UUID.randomUUID())
                .email("gone@example.com")
                .firstName("Bob")
                .language("de")
                .source("explicit")
                .unsubscribeToken("token-xyz")
                .subscribedAt(Instant.now().minusSeconds(86400))
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

    // ── Story 10.28: Subscriber Management ──────────────────────────────────

    @Nested
    @DisplayName("unsubscribeById (Story 10.28)")
    class UnsubscribeById {

        @Test
        @DisplayName("should_unsubscribeSubscriber_when_idExists")
        void should_unsubscribeSubscriber_when_idExists() {
            when(subscriberRepository.findById(activeSubscriber.getId()))
                    .thenReturn(Optional.of(activeSubscriber));
            when(subscriberRepository.save(any(NewsletterSubscriber.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            NewsletterSubscriber result = newsletterSubscriberService.unsubscribeById(activeSubscriber.getId());

            assertThat(result.getUnsubscribedAt()).isNotNull();
            verify(subscriberRepository).save(activeSubscriber);
        }

        @Test
        @DisplayName("should_throwConflict_when_subscriberAlreadyUnsubscribed")
        void should_throwConflict_when_subscriberAlreadyUnsubscribed() {
            when(subscriberRepository.findById(alreadyUnsubscribedSubscriber.getId()))
                    .thenReturn(Optional.of(alreadyUnsubscribedSubscriber));

            assertThatThrownBy(() -> newsletterSubscriberService.unsubscribeById(alreadyUnsubscribedSubscriber.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }

        @Test
        @DisplayName("should_throw404_when_subscriberNotFound")
        void should_throw404_when_subscriberNotFound() {
            UUID missingId = UUID.randomUUID();
            when(subscriberRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> newsletterSubscriberService.unsubscribeById(missingId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("resubscribeById (Story 10.28)")
    class ResubscribeById {

        @Test
        @DisplayName("should_resubscribeSubscriber_when_subscriberIsUnsubscribed")
        void should_resubscribeSubscriber_when_subscriberIsUnsubscribed() {
            when(subscriberRepository.findById(alreadyUnsubscribedSubscriber.getId()))
                    .thenReturn(Optional.of(alreadyUnsubscribedSubscriber));
            when(subscriberRepository.save(any(NewsletterSubscriber.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            NewsletterSubscriber result = newsletterSubscriberService.resubscribeById(alreadyUnsubscribedSubscriber.getId());

            assertThat(result.getUnsubscribedAt()).isNull();
            assertThat(result.getSubscribedAt()).isNotNull();
            verify(subscriberRepository).save(alreadyUnsubscribedSubscriber);
        }

        @Test
        @DisplayName("should_throwConflict_when_subscriberAlreadyActive")
        void should_throwConflict_when_subscriberAlreadyActive() {
            when(subscriberRepository.findById(activeSubscriber.getId()))
                    .thenReturn(Optional.of(activeSubscriber));

            assertThatThrownBy(() -> newsletterSubscriberService.resubscribeById(activeSubscriber.getId()))
                    .isInstanceOf(IllegalStateException.class);
        }
    }

    @Nested
    @DisplayName("deleteById (Story 10.28)")
    class DeleteById {

        @Test
        @DisplayName("should_deleteSubscriber_when_idExists")
        void should_deleteSubscriber_when_idExists() {
            when(subscriberRepository.findById(activeSubscriber.getId()))
                    .thenReturn(Optional.of(activeSubscriber));

            newsletterSubscriberService.deleteById(activeSubscriber.getId());

            verify(subscriberRepository).delete(activeSubscriber);
        }

        @Test
        @DisplayName("should_throw404_when_deletingNonExistentSubscriber")
        void should_throw404_when_deletingNonExistentSubscriber() {
            UUID missingId = UUID.randomUUID();
            when(subscriberRepository.findById(missingId)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> newsletterSubscriberService.deleteById(missingId))
                    .isInstanceOf(NoSuchElementException.class);
        }
    }

    @Nested
    @DisplayName("findSubscribers (Story 10.28)")
    class FindSubscribers {

        @Test
        @DisplayName("should_filterBySearch_when_searchParamProvided")
        void should_filterBySearch_when_searchParamProvided() {
            PaginationParams params = PaginationParams.builder().page(1).limit(20).build();
            when(subscriberRepository.findFiltered(eq("alice"), eq("%alice%"), eq("all"), any(Pageable.class)))
                    .thenReturn(List.of(activeSubscriber));

            List<NewsletterSubscriber> result = newsletterSubscriberService.findSubscribers("Alice", "all", "subscribedAt", "desc", params);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should_filterByStatus_when_statusIsActive")
        void should_filterByStatus_when_statusIsActive() {
            PaginationParams params = PaginationParams.builder().page(1).limit(20).build();
            when(subscriberRepository.findFiltered(any(), any(), eq("active"), any(Pageable.class)))
                    .thenReturn(List.of(activeSubscriber));

            List<NewsletterSubscriber> result = newsletterSubscriberService.findSubscribers(null, "active", "subscribedAt", "desc", params);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should_filterByStatus_when_statusIsUnsubscribed")
        void should_filterByStatus_when_statusIsUnsubscribed() {
            PaginationParams params = PaginationParams.builder().page(1).limit(20).build();
            when(subscriberRepository.findFiltered(any(), any(), eq("unsubscribed"), any(Pageable.class)))
                    .thenReturn(List.of(alreadyUnsubscribedSubscriber));

            List<NewsletterSubscriber> result = newsletterSubscriberService.findSubscribers(null, "unsubscribed", "subscribedAt", "desc", params);

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("should_paginateCorrectly_when_pageAndLimitProvided")
        void should_paginateCorrectly_when_pageAndLimitProvided() {
            PaginationParams params = PaginationParams.builder().page(2).limit(10).build();
            when(subscriberRepository.findFiltered(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            newsletterSubscriberService.findSubscribers(null, "all", "subscribedAt", "desc", params);

            verify(subscriberRepository).findFiltered(any(), any(), any(),
                    eq(PageRequest.of(1, 10, Sort.by(Sort.Direction.DESC, "subscribedAt"))));
        }

        @Test
        @DisplayName("should_sortByEmail_when_sortByEmailRequested")
        void should_sortByEmail_when_sortByEmailRequested() {
            PaginationParams params = PaginationParams.builder().page(1).limit(20).build();
            when(subscriberRepository.findFiltered(any(), any(), any(), any(Pageable.class)))
                    .thenReturn(List.of());

            newsletterSubscriberService.findSubscribers(null, "all", "email", "asc", params);

            verify(subscriberRepository).findFiltered(any(), any(), any(),
                    eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "email"))));
        }
    }

    @Nested
    @DisplayName("countSubscribers (Story 10.28)")
    class CountSubscribers {

        @Test
        @DisplayName("should_countWithSearch_when_searchProvided")
        void should_countWithSearch_when_searchProvided() {
            when(subscriberRepository.countFiltered(eq("alice"), eq("%alice%"), eq("all")))
                    .thenReturn(5L);

            long count = newsletterSubscriberService.countSubscribers("Alice", "all");

            assertThat(count).isEqualTo(5L);
        }

        @Test
        @DisplayName("should_countAll_when_noFilters")
        void should_countAll_when_noFilters() {
            when(subscriberRepository.countFiltered(any(), any(), eq("all")))
                    .thenReturn(10L);

            long count = newsletterSubscriberService.countSubscribers(null, "all");

            assertThat(count).isEqualTo(10L);
        }
    }

    @Nested
    @DisplayName("toResponse — includes unsubscribedAt (Story 10.28)")
    class ToResponse {

        @Test
        @DisplayName("should_includeUnsubscribedAt_when_subscriberIsUnsubscribed")
        void should_includeUnsubscribedAt_when_subscriberIsUnsubscribed() {
            var response = newsletterSubscriberService.toResponse(alreadyUnsubscribedSubscriber);
            assertThat(response.getUnsubscribedAt()).isNotNull();
        }

        @Test
        @DisplayName("should_haveNullUnsubscribedAt_when_subscriberIsActive")
        void should_haveNullUnsubscribedAt_when_subscriberIsActive() {
            var response = newsletterSubscriberService.toResponse(activeSubscriber);
            assertThat(response.getUnsubscribedAt()).isNull();
        }
    }
}
