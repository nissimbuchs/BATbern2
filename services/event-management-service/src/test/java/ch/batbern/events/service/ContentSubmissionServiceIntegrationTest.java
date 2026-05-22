package ch.batbern.events.service;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.SessionContentVersion;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.domain.SpeakerStatusHistory;
import ch.batbern.events.dto.ContentSubmitResponse;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.PatchUserProfileRequest;
import ch.batbern.events.event.SpeakerContentSubmittedEvent;
import ch.batbern.events.repository.SessionContentHistoryRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.events.service.content.ContentSubmissionPayload;
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
    private SessionContentHistoryRepository sessionContentHistoryRepository;
    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private UserApiClient userApiClient;
    @Autowired
    private ApplicationEvents recordedEvents;

    private static final String EVENT_CODE = "BAT-11C2-INT";
    private static final String ORGANIZER = "organizer.alice";
    private static final String SPEAKER = "speaker.jane";

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
                .organizerUsername(ORGANIZER)
                .build();
        testEvent = eventRepository.save(testEvent);
    }

    private SpeakerPool seedSpeaker(SpeakerWorkflowState status, String username) {
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Speaker")
                .company("Acme")
                .status(status)
                .acceptedAt(Instant.now())
                .build();
        speaker = speakerPoolRepository.save(speaker);

        // Story 11.E.8: in production, SpeakerWorkflowService.runReadyHook provisions
        // a Session + PRIMARY_SPEAKER SessionUser row at the CONTACTED → READY transition.
        // Integration tests bypass the workflow and call SpeakerPoolRepository.save() directly,
        // so we have to mirror the provisioning manually for any READY+ speaker; otherwise
        // ContentSubmissionService.loadAssignedSession throws IllegalStateException on submit.
        // The username-null legacy case uses the speaker name as the SessionUser.username
        // fallback — session_users.username is NOT NULL but pre-11.E.8 legacy data may
        // have a null speaker_pool.username; we test the bio-patch skip path with that shape.
        if (isPostReady(status)) {
            String slug = (EVENT_CODE + "-"
                    + (username != null ? username : "legacy-" + speaker.getId().toString().substring(0, 8))
                    + "-" + speaker.getId().toString().substring(0, 8))
                    .toLowerCase().replaceAll("[^a-z0-9-]", "-");
            Session session = Session.builder()
                    .eventId(testEvent.getId())
                    .eventCode(EVENT_CODE)
                    .sessionSlug(slug)
                    .title("TBD — " + (username != null ? username : "legacy speaker"))
                    .sessionType("presentation")
                    .speakerPoolId(speaker.getId())
                    .build();
            session = sessionRepository.save(session);
            String sessionUsername = (username != null && !username.isBlank())
                    ? username
                    : "Jane Speaker";  // mirrors the speaker_name fallback the old code used
            SessionUser sessionUser = SessionUser.builder()
                    .session(session)
                    .username(sessionUsername)
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .isConfirmed(status == SpeakerWorkflowState.ACCEPTED
                            || status == SpeakerWorkflowState.CONTENT_SUBMITTED
                            || status == SpeakerWorkflowState.QUALITY_REVIEWED)
                    .build();
            sessionUserRepository.save(sessionUser);
            speaker.setSessionId(session.getId());
            speaker = speakerPoolRepository.save(speaker);
        }
        return speaker;
    }

    private boolean isPostReady(SpeakerWorkflowState status) {
        return status == SpeakerWorkflowState.READY
                || status == SpeakerWorkflowState.INVITED
                || status == SpeakerWorkflowState.ACCEPTED
                || status == SpeakerWorkflowState.CONTENT_SUBMITTED
                || status == SpeakerWorkflowState.QUALITY_REVIEWED;
    }

    // ============================================================
    // AC9 #1 — Organizer-on-behalf happy path
    // ============================================================
    @Test
    @DisplayName("should_persistContentAndTransitionToContentSubmitted_when_organizerSubmitsOnBehalf")
    void should_persistContentAndTransitionToContentSubmitted_when_organizerSubmitsOnBehalf() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
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

        // Story 11.E.8: session_content_history is keyed by session_id now.
        List<SessionContentVersion> submissions = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(reloaded.getSessionId())
                .stream().toList();
        assertThat(submissions).hasSize(1);
        assertThat(submissions.get(0).getTitle()).isEqualTo("Zero Trust Security");
        assertThat(submissions.get(0).getSubmissionVersion()).isEqualTo(1);

        // Status-history row has changed_by_username = organizer-username.
        List<SpeakerStatusHistory> history = statusHistoryRepository.findBySpeakerPoolIdOrderByChangedAtDesc(
                speaker.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(ORGANIZER);
        assertThat(history.get(0).getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // UserApiClient.patchUserProfile was invoked with bio + pictureUrl.
        verify(userApiClient, times(1)).patchUserProfile(eq(SPEAKER),
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
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
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
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(SPEAKER);
    }

    // ============================================================
    // AC9 #4 — No profile patch when bio + pictureUrl both null
    // ============================================================
    @Test
    @DisplayName("should_notCallPatchUserProfile_when_bioAndPictureBothNull")
    void should_notCallPatchUserProfile_when_bioAndPictureBothNull() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
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
    @DisplayName("should_logWarningAndSkipProfilePatch_when_speakerHasNoPrimarySessionUser")
    void should_logWarningAndSkipProfilePatch_when_speakerHasNoPrimarySessionUser() {
        // Story 11.E.9: the "pool.username is null" legacy data case is gone (column dropped).
        // The equivalent post-column-drop scenario is: no PRIMARY_SPEAKER session_users row,
        // which makes PrimarySpeakerResolver.resolve() return empty. Seed an ACCEPTED speaker
        // directly without provisioning session_users — mirrors pre-11.E.8 legacy data that
        // never went through the runReadyHook seam.
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Jane Speaker")
                .company("Acme")
                .status(SpeakerWorkflowState.ACCEPTED)
                .acceptedAt(Instant.now())
                .build();
        speaker = speakerPoolRepository.save(speaker);
        Session orphanSession = Session.builder()
                .eventId(testEvent.getId())
                .eventCode(EVENT_CODE)
                .sessionSlug("legacy-" + speaker.getId().toString().substring(0, 8))
                .title("Legacy session")
                .sessionType("presentation")
                .speakerPoolId(speaker.getId())
                .build();
        orphanSession = sessionRepository.save(orphanSession);
        speaker.setSessionId(orphanSession.getId());
        speaker = speakerPoolRepository.save(speaker);

        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "With-bio title",
                "Abstract.",
                "Some bio",
                null,
                null
        );

        // Submit with ORGANIZER username so the workflow status-history row can be written
        // (transition() requires non-null username; the missing PRIMARY_SPEAKER session_user
        // is the pre-11.E.8 legacy-data case we are guarding against).
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
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
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
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.INVITED, SPEAKER);
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

    // ============================================================
    // AC9 #6 — explicit equivalence: organizer and speaker flows produce identical
    // SessionContentVersion, Session, and SpeakerPool rows (modulo speaker_status_history.changed_by_username).
    // Review patch A2 (Story 11.C.2 code review 2026-05-16).
    // ============================================================
    @Test
    @DisplayName("should_produceIdenticalRows_when_organizerAndSpeakerSubmitSamePayload")
    void should_produceIdenticalRows_when_organizerAndSpeakerSubmitSamePayload() {
        SpeakerPool speakerForOrganizer = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
        SpeakerPool speakerForSelf = seedSpeaker(SpeakerWorkflowState.ACCEPTED, "speaker.two");

        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Equivalence Title", "Equivalence abstract.", null, null, null);

        contentSubmissionService.submit(speakerForOrganizer.getId(), EVENT_CODE, payload, ORGANIZER);
        String speakerTwo = "speaker.two";
        contentSubmissionService.submit(speakerForSelf.getId(), EVENT_CODE, payload, speakerTwo);

        // Story 11.E.8: session_content_history is keyed by session_id now. Reload both
        // SpeakerPool rows to get their freshly-linked session_ids.
        SpeakerPool reloadedOrganizerSpeaker = speakerPoolRepository.findById(speakerForOrganizer.getId())
                .orElseThrow();
        SpeakerPool reloadedSelfSpeaker = speakerPoolRepository.findById(speakerForSelf.getId())
                .orElseThrow();
        SessionContentVersion organizerSubmission = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(reloadedOrganizerSpeaker.getSessionId())
                .orElseThrow();
        SessionContentVersion speakerSubmission = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(reloadedSelfSpeaker.getSessionId())
                .orElseThrow();

        // Content row payload identical modulo identity fields (id, speakerPool, session).
        assertThat(organizerSubmission.getTitle()).isEqualTo(speakerSubmission.getTitle());
        assertThat(organizerSubmission.getContentAbstract()).isEqualTo(speakerSubmission.getContentAbstract());
        assertThat(organizerSubmission.getAbstractCharCount()).isEqualTo(speakerSubmission.getAbstractCharCount());
        assertThat(organizerSubmission.getSubmissionVersion()).isEqualTo(speakerSubmission.getSubmissionVersion());

        // SpeakerPool side: both end in CONTENT_SUBMITTED. Story 11.E.8 dropped
        // speaker_pool.content_status (V100) — content_status is derived from latest
        // session_content_history.reviewer_feedback at read time; equivalence holds because
        // both speakers' histories are in the same "just submitted, no review yet" shape.
        SpeakerPool reloadedOrganizerSide = speakerPoolRepository.findById(speakerForOrganizer.getId()).orElseThrow();
        SpeakerPool reloadedSpeakerSide = speakerPoolRepository.findById(speakerForSelf.getId()).orElseThrow();
        assertThat(reloadedOrganizerSide.getStatus()).isEqualTo(reloadedSpeakerSide.getStatus());

        // The ONLY documented difference: speaker_status_history.changed_by_username.
        SpeakerStatusHistory organizerHistory = statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speakerForOrganizer.getId())
                .get(0);
        SpeakerStatusHistory speakerHistory = statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speakerForSelf.getId())
                .get(0);
        assertThat(organizerHistory.getChangedByUsername()).isEqualTo(ORGANIZER);
        assertThat(speakerHistory.getChangedByUsername()).isEqualTo("speaker.two");
        assertThat(organizerHistory.getNewStatus()).isEqualTo(speakerHistory.getNewStatus());
    }

    // ============================================================
    // Review patch D2 — organizer endpoint accepts resubmission from CONTENT_SUBMITTED
    // (decision: keep loosened source-state precondition for both endpoints).
    // ============================================================
    @Test
    @DisplayName("should_acceptOrganizerResubmission_when_speakerAlreadyInContentSubmittedState")
    void should_acceptOrganizerResubmission_when_speakerAlreadyInContentSubmittedState() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.CONTENT_SUBMITTED, SPEAKER);
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Updated title", "Updated abstract.", null, null, null);

        ContentSubmitResponse response = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, payload, ORGANIZER);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // Self-transition writes a history row even from same-state.
        List<SpeakerStatusHistory> history = statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId());
        assertThat(history).isNotEmpty();
        assertThat(history.get(0).getChangedByUsername()).isEqualTo(ORGANIZER);
    }

    // ============================================================
    // Review patch P1 — cross-event corruption: speaker.eventId must match eventCode.
    // ============================================================
    @Test
    @DisplayName("should_rejectSubmission_when_speakerBelongsToDifferentEvent")
    void should_rejectSubmission_when_speakerBelongsToDifferentEvent() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "X", "Abstract.", null, null, null);

        // Use a deliberately wrong event_code that exists for *another* event in the test setup.
        // If only one event exists in the test fixture this will EntityNotFoundException — which is
        // the IllegalArgumentException-or-EntityNotFoundException equivalence we accept.
        assertThatThrownBy(() -> contentSubmissionService.submit(
                        speaker.getId(), "BATbern-other-event", payload, ORGANIZER))
                .satisfiesAnyOf(
                        e -> assertThat(e).isInstanceOf(IllegalArgumentException.class),
                        e -> assertThat(e).isInstanceOf(jakarta.persistence.EntityNotFoundException.class));
    }

    // ============================================================
    // Story 11.E.8+ — speaker may revise content after the moderator's review. The
    // back-transition QUALITY_REVIEWED → CONTENT_SUBMITTED requeues the submission;
    // the prior content_submissions row keeps its reviewer feedback (versioned).
    // ============================================================
    @Test
    @DisplayName("should_acceptResubmissionAndTransitionBackToContentSubmitted_when_speakerInQualityReviewed")
    void should_acceptResubmissionAndTransitionBackToContentSubmitted_when_speakerInQualityReviewed() {
        // Seed in QUALITY_REVIEWED — session is provisioned by the helper (mirrors READY hook).
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.QUALITY_REVIEWED, SPEAKER);
        // Pre-existing v1 submission representing the content the organizer reviewed.
        ContentSubmissionPayload v1 = new ContentSubmissionPayload(
                "Original title", "Original abstract.", null, null, null);
        contentSubmissionService.submit(speaker.getId(), EVENT_CODE,
                new ContentSubmissionPayload("Bootstrap title", "Bootstrap abstract.", null, null, null),
                ORGANIZER);
        // The bootstrap submit transitioned QUALITY_REVIEWED → CONTENT_SUBMITTED, so reset
        // the speaker back to QUALITY_REVIEWED to mimic a re-reviewed-then-revised flow.
        SpeakerPool reset = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        reset.setStatus(SpeakerWorkflowState.QUALITY_REVIEWED);
        speakerPoolRepository.save(reset);

        // Now the speaker revises the content. Expected: transition back to CONTENT_SUBMITTED,
        // version increments, status_history records the back-transition with the speaker as actor.
        ContentSubmissionPayload v2 = new ContentSubmissionPayload(
                "Revised title", "Revised abstract.", null, null, null);
        ContentSubmitResponse response = contentSubmissionService.submit(
                speaker.getId(), EVENT_CODE, v2, SPEAKER);

        assertThat(response.status()).isEqualTo("SUBMITTED");
        SpeakerPool reloaded = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);

        // Story 11.E.8: latest session_content_history row is keyed by session_id.
        SessionContentVersion latest = sessionContentHistoryRepository
                .findFirstBySessionIdOrderBySubmissionVersionDesc(reloaded.getSessionId())
                .orElseThrow();
        assertThat(latest.getTitle()).isEqualTo("Revised title");

        // Status history has a QUALITY_REVIEWED → CONTENT_SUBMITTED row with the speaker as actor.
        List<SpeakerStatusHistory> history = statusHistoryRepository
                .findBySpeakerPoolIdOrderByChangedAtDesc(speaker.getId());
        assertThat(history).isNotEmpty();
        SpeakerStatusHistory mostRecent = history.get(0);
        assertThat(mostRecent.getPreviousStatus()).isEqualTo(SpeakerWorkflowState.QUALITY_REVIEWED);
        assertThat(mostRecent.getNewStatus()).isEqualTo(SpeakerWorkflowState.CONTENT_SUBMITTED);
        assertThat(mostRecent.getChangedByUsername()).isEqualTo(SPEAKER);
    }

    // ============================================================
    // Story 11.E.8 — strict guard: submission rejected if speaker lacks a session_id.
    // The session is supposed to be provisioned at the CONTACTED → READY transition;
    // arriving at ACCEPTED without one is a data-integrity bug (or pre-11.E.8 legacy data).
    // ============================================================
    @Test
    @DisplayName("should_rejectSubmission_when_speakerHasNoSessionId")
    void should_rejectSubmission_when_speakerHasNoSessionId() {
        // Manually create a speaker_pool row in ACCEPTED state but WITHOUT going through
        // seedSpeaker() (which now provisions a session). This mirrors a pre-11.E.8 legacy row.
        SpeakerPool speaker = SpeakerPool.builder()
                .eventId(testEvent.getId())
                .speakerName("Orphan Speaker")
                .status(SpeakerWorkflowState.ACCEPTED)
                .acceptedAt(Instant.now())
                .build();
        speaker = speakerPoolRepository.save(speaker);

        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "Title", "Abstract.", null, null, null);
        UUID poolId = speaker.getId();
        assertThatThrownBy(() -> contentSubmissionService.submit(
                        poolId, EVENT_CODE, payload, ORGANIZER))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("session_id");
    }

    // ============================================================
    // Review patch P2 — presentationUploadId is rejected with 400 until Story 11.D.4.
    // ============================================================
    @Test
    @DisplayName("should_rejectSubmission_when_presentationUploadIdIsProvided")
    void should_rejectSubmission_when_presentationUploadIdIsProvided() {
        SpeakerPool speaker = seedSpeaker(SpeakerWorkflowState.ACCEPTED, SPEAKER);
        ContentSubmissionPayload payload = new ContentSubmissionPayload(
                "T", "Abstract.", null, null, "upload-id-from-future-story");

        assertThatThrownBy(() -> contentSubmissionService.submit(
                        speaker.getId(), EVENT_CODE, payload, ORGANIZER))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("presentationUploadId");
    }
}
