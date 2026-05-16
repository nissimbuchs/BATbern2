package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.ContentSubmission;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.ContentSubmissionRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.content.ContentSubmissionPayload;
import ch.batbern.events.service.workflow.SecurityPrincipal;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.event.ApplicationEvents;
import org.springframework.test.context.event.RecordApplicationEvents;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Integration tests for the consolidated {@link ContentSubmissionService#submit
 * ContentSubmissionService.submit(...)} method introduced by Story 11.C.2 (AC4).
 *
 * <p>Runs against real PostgreSQL via Testcontainers (per project-context.md and CLAUDE.md:
 * never H2). Covers the AC9 cases that exercise the shared backend write-path:
 * <ul>
 *   <li>AC9 #1 — organizer-on-behalf happy path</li>
 *   <li>AC9 #2 — speaker-self happy path</li>
 *   <li>AC9 #4 — no profile patch when bio and pictureUrl are both null</li>
 *   <li>AC9 #9 — missing username invariant violation (warn + skip patch, still submit)</li>
 *   <li>Resubmission semantics (AC9 #3 — bonus coverage of same-state self-transition)</li>
 * </ul>
 *
 * <p>The cross-service {@link UserApiClient} HTTP boundary is mocked via
 * {@link TestUserApiClientConfig} (no second Testcontainer for CUMS in EMS tests);
 * {@link ApplicationEventPublisher} is spied to assert event publication.
 */
@Transactional
@Import(TestUserApiClientConfig.class)
@RecordApplicationEvents
class ContentSubmissionServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ContentSubmissionService contentSubmissionService;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private ContentSubmissionRepository contentSubmissionRepository;
    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private UserApiClient userApiClient;
    @Autowired
    private ApplicationEvents recordedEvents;

    private static final String EVENT_CODE = "BAT-11C2-INT";
    private static final SecurityPrincipal ORGANIZER =
            new SecurityPrincipal("organizer.alice", List.of("ORGANIZER"));
    private static final SecurityPrincipal SPEAKER =
            new SecurityPrincipal("speaker.jane", List.of("SPEAKER"));

    private Event testEvent;

    @BeforeEach
    void setUp() {
        testEvent = Event.builder()
                .eventCode(EVENT_CODE)
                .eventNumber(11202)
                .title("Story 11.C.2 Integration")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(45, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("123 Test Street")
                .venueCapacity(100)
                .organizerUsername(ORGANIZER.username())
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    private SpeakerPool seedSpeaker(SpeakerWorkflowState status, String username) {
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Speaker")
                .email("jane@example.com")
                .company("Acme")
                .username(username)
                .status(status)
                .contentStatus("PENDING")
                .acceptedAt(Instant.now())
                .build();
        return speakerPoolRepository.save(speaker);
    }

    // ============================================================
    // AC9 #1 — Organizer-on-behalf happy path
    // ============================================================
    @Test
    @DisplayName("should_persistContentAndTransitionToContentSubmitted_when_organizerSubmitsOnBehalf")
    void should_persistContentAndTransitionToContentSubmitted_when_organizerSubmitsOnBehalf() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER.username());
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Zero Trust Security",
                "Abstract about Zero Trust principles.",
                "Updated bio for this event.",
                "https://cdn.test.com/jane.jpg",
                null
        );

        ContentSubmitResponse response = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, payload, ORGANIZER);

        assertThat(response.submissionId()).isNotNull();
        assertThat(response.version()).isEqualTo(1);
        assertThat(response.status()).isEqualTo("SUBMITTED");

        // Speaker state is CONTENT_SUBMITTED (written by SpeakerWorkflowService.transition).
        SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // Content row exists with version 1.
        List<ContentSubmission> submissions = contentSubmissionRepository
                .findFirstBySpeakerPoolIdOrderBySubmissionVersionDesc(speaker.getId())
                .stream().toList();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getTitle()).isEqualTo("Zero Trust Security");
        assertThat(submissions.get(0).getSubmissionVersion()).isEqualTo(1);

        // Status-history row has changed_by_username = organizer-username.
        List<SpeakerStatusHistory> history = statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(
                speaker.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(ORGANIZER.username());
        assertThat(history.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // UserApiClient.patchUserProfile was invoked with bio + pictureUrl.
        verify(userApiClient, times(1)).patchUserProfile(eq(SPEAKER.username()),
                any(PatchUserProfileRequest.class));

        // SpeakerContentSubmittedEvent was published.
        assertThat(recordedEvents.stream(SpeakerContentSubmittedEvent.class).count()).isEqualTo(1);
    }

    // ============================================================
    // AC9 #2 — Speaker-self happy path (audit row carries speaker username)
    // ============================================================
    @Test
    @DisplayName("should_persistContentAndTransitionToContentSubmitted_when_speakerSubmitsSelf")
    void should_persistContentAndTransitionToContentSubmitted_when_speakerSubmitsSelf() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER.username());
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Speaker Self Title",
                "Speaker-side abstract.",
                null,
                null,
                null
        );

        ContentSubmitResponse response = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, payload, SPEAKER);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        List<SpeakerStatusHistory> history = statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(
                speaker.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(SPEAKER.username());
    }

    // ============================================================
    // AC9 #4 — No profile patch when bio + pictureUrl both null
    // ============================================================
    @Test
    @DisplayName("should_notCallPatchUserProfile_when_bioAndPictureBothNull")
    void should_notCallPatchUserProfile_when_bioAndPictureBothNull() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER.username());
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "No-profile-patch title",
                "Abstract.",
                null,
                null,
                null
        );

        contentSubmissionService.submit(speaker.getId(), EVENT_CODE, payload, ORGANIZER);

        verify(userApiClient, never()).patchUserProfile(any(), any());
    }

    // ============================================================
    // AC9 #9 — Missing username invariant: warn + skip patch, still submit
    // ============================================================
    @Test
    @DisplayName("should_logWarningAndSkipProfilePatch_when_speakerUsernameIsNull")
    void should_logWarningAndSkipProfilePatch_when_speakerUsernameIsNull() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, null);
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "With-bio title",
                "Abstract.",
                "Some bio",
                null,
                null
        );

        // Submit with ORGANIZER principal so the workflow status-history row can be written
        // (SecurityPrincipal requires non-null username; the speaker.username being null is the
        // pre-11.B.2 legacy data case we are guarding against).
        ContentSubmitResponse response = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, payload, ORGANIZER);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        verify(userApiClient, never()).patchUserProfile(any(), any());
    }

    // ============================================================
    // Bonus — resubmission case (AC9 #3): CONTENT_SUBMITTED → CONTENT_SUBMITTED
    // writes a self-transition history row and increments the content version.
    // ============================================================
    @Test
    @DisplayName("should_writeSelfTransitionHistory_when_resubmittingFromContentSubmitted")
    void should_writeSelfTransitionHistory_when_resubmittingFromContentSubmitted() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER.username());
        ContentSubmissionPayload v1 = new ContentSubmissionPayload(
                "First title", "First abstract.", null, null, null);

        ContentSubmitResponse first = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, v1, SPEAKER);
        assertThat(first.version()).isEqualTo(1);

        // Resubmit while the speaker is already in CONTENT_SUBMITTED.
        ContentSubmissionPayload v2 = new ContentSubmissionPayload(
                "Second title", "Second abstract.", null, null, null);
        ContentSubmitResponse second = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, v2, SPEAKER);
        assertThat(second.version()).isEqualTo(2);

        SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // Two history rows: ACCEPTED → CONTENT_SUBMITTED and CONTENT_SUBMITTED → CONTENT_SUBMITTED.
        List<SpeakerStatusHistory> history = statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(
                speaker.getId());
        assertThat(history.size()).isGreaterThanOrEqualTo(2);
        assertThat(history.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        assertThat(history.get(0).getPreviousStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
    }

    // ============================================================
    // Source-state precondition — submit from a non-ACCEPTED, non-CONTENT_SUBMITTED state.
    // ============================================================
    @Test
    @DisplayName("should_rejectSubmission_when_speakerNotInAcceptedOrContentSubmittedState")
    void should_rejectSubmission_when_speakerNotInAcceptedOrContentSubmittedState() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED, SPEAKER.username());
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Title", "Abstract.", null, null, null);

        assertThatThrownBy(() -> contentSubmissionService.submit(
                        speaker.getId(), EVENT_CODE, payload, ORGANIZER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ACCEPTED")
                .hasMessageContaining("CONTENT_SUBMITTED");
    }

    // ============================================================
    // Not-found — submit for a non-existent speaker pool ID.
    // ============================================================
    @Test
    @DisplayName("should_throwNotFound_when_speakerPoolIdDoesNotExist")
    void should_throwNotFound_when_speakerPoolIdDoesNotExist() {
        UUID missingId = UUID.randomUUID();
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Title", "Abstract.", null, null, null);

        assertThatThrownBy(() -> contentSubmissionService.submit(
                        missingId, EVENT_CODE, payload, ORGANIZER))
                .isInstanceOf(jakarta.persistence.EntityNotFoundException.class)
                .hasMessageContaining("Speaker not found");
    }
}
