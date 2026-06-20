package ch.batbern.events.service;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.QnaWindowStatus;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionQnaNotification;
import ch.batbern.events.domain.SessionQnaPost;
import ch.batbern.events.domain.SessionQnaWindow;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SessionQnaNotificationRepository;
import ch.batbern.events.repository.SessionQnaPostRepository;
import ch.batbern.events.repository.SessionQnaWindowRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.core.SimpleLock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Integration tests for {@link QnaNotificationService} (Story 15.7, PostgreSQL/Testcontainers).
 *
 * <p>ShedLock: the flush is {@code @SchedulerLock}-annotated; a <b>class-scoped
 * {@code @MockitoBean LockProvider}</b> grants a no-op lock so the committed lock row never leaks
 * across rolled-back test transactions (NOT a {@code @TestConfiguration @Primary} bean).
 *
 * <p>{@link EmailService} is a {@code @MockitoSpyBean} — its real rendering runs (so the count is
 * substituted into the body), but the actual SES send is the test-profile no-op; we verify the
 * send call + capture its arguments. {@link UserApiClient} is a {@code @MockitoBean} stubbed
 * per-recipient (cadence preference, email, locale).
 *
 * <p><b>Not {@code @Transactional}.</b> The service under test is intentionally non-transactional
 * and persists each recipient's throttle row via a {@code REQUIRES_NEW} writer — so setup data must
 * be committed (a rolled-back test tx would be invisible to the flush's own read transactions and
 * its FK target would not exist). Cleanup is explicit in {@code @BeforeEach}/{@code @AfterEach}.
 */
class QnaNotificationServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private QnaNotificationService qnaNotificationService;

    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private SessionQnaWindowRepository windowRepository;
    @Autowired
    private SessionQnaPostRepository postRepository;
    @Autowired
    private SessionQnaNotificationRepository notificationRepository;

    @MockitoBean
    private LockProvider lockProvider;

    @MockitoSpyBean
    private EmailService emailService;

    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern970";
    private static final String SLUG = "live-qna-session";

    private static final String SPEAKER = "primary.speaker";
    private static final String CO_SPEAKER = "co.speaker";
    private static final String MODERATOR = "the.moderator";
    private static final String PANELIST = "the.panelist";
    private static final String ATTENDEE = "jane.attendee";

    @BeforeEach
    void setUp() {
        // Committed cleanup (this test class is not @Transactional — see class javadoc).
        cleanDb();

        when(lockProvider.lock(any())).thenReturn(Optional.of(mock(SimpleLock.class)));

        // Default stubs (lenient — not every test reads every recipient).
        lenient().when(userApiClient.getQnaNotificationFrequency(anyString())).thenReturn("live");
        lenient().when(userApiClient.getPreferredLanguage(anyString())).thenReturn("en");
        lenient().when(userApiClient.getEmailByUsername(SPEAKER)).thenReturn("primary.speaker@batbern.ch");
        lenient().when(userApiClient.getEmailByUsername(CO_SPEAKER)).thenReturn("co.speaker@batbern.ch");
        lenient().when(userApiClient.getEmailByUsername(MODERATOR)).thenReturn("the.moderator@batbern.ch");
        lenient().when(userApiClient.getEmailByUsername(PANELIST)).thenReturn("the.panelist@batbern.ch");
    }

    @AfterEach
    void tearDown() {
        // The REQUIRES_NEW writer commits notification rows that would otherwise leak to other
        // tests / test classes (this class is not @Transactional). Clean them up.
        cleanDb();
    }

    private void cleanDb() {
        // deleteAllInBatch (bulk DELETE) — not deleteAll() — to avoid StaleObjectStateException
        // from per-entity removal of rows that this non-transactional class committed in a prior
        // test. FK-safe child→parent order.
        notificationRepository.deleteAllInBatch();
        postRepository.deleteAllInBatch();
        sessionUserRepository.deleteAllInBatch();
        windowRepository.deleteAllInBatch();
        sessionRepository.deleteAllInBatch();
        eventRepository.deleteAllInBatch();
    }

    // ==================== AC4: speaker + co-speaker + moderator all notified ====================

    @Test
    @DisplayName("AC4: one new top-level question notifies speaker + co-speaker + moderator (not panelist)")
    void should_notifySpeakerCoSpeakerModerator_when_newQuestion() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        saveSpeaker(session, CO_SPEAKER, SessionUser.SpeakerRole.CO_SPEAKER);
        saveSpeaker(session, MODERATOR, SessionUser.SpeakerRole.MODERATOR);
        saveSpeaker(session, PANELIST, SessionUser.SpeakerRole.PANELIST);
        savePost(window, ATTENDEE, "How did the migration go?", null, Instant.now());

        qnaNotificationService.flushPending();

        ArgumentCaptor<String> to = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(3)).sendHtmlEmailSync(to.capture(), anyString(), anyString());
        assertThat(to.getAllValues()).containsExactlyInAnyOrder(
                "primary.speaker@batbern.ch", "co.speaker@batbern.ch", "the.moderator@batbern.ch");
        // Panelist never emailed.
        verify(emailService, never()).sendHtmlEmailSync(eq("the.panelist@batbern.ch"), anyString(), anyString());

        assertThat(notificationRepository.count()).isEqualTo(3);
    }

    // ==================== AC5 (live): digest windowing ====================

    @Test
    @DisplayName("AC5: five questions in the live window send ONE email saying '5' per recipient")
    void should_sendOneDigestSayingFive_when_fiveQuestionsWithinWindow() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        for (int i = 0; i < 5; i++) {
            savePost(window, ATTENDEE, "Question " + i, null, Instant.now().plusMillis(i));
        }

        qnaNotificationService.flushPending();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), body.capture());
        assertThat(body.getValue()).contains("5");
    }

    @Test
    @DisplayName("AC5: a second flush within the live window sends nothing more (throttled)")
    void should_notResend_when_secondFlushWithinWindow() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "First", null, Instant.now());

        qnaNotificationService.flushPending();
        qnaNotificationService.flushPending(); // immediately again — within the 15-min window

        verify(emailService, times(1)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("AC5: after the window elapses, a new digest counts only NEW questions (water mark)")
    void should_countOnlyNewQuestions_when_windowElapsedAndMoreArrive() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now().minus(30, ChronoUnit.MINUTES));

        qnaNotificationService.flushPending(); // sends "1"

        // Simulate the live window having elapsed: push lastNotifiedAt back beyond 15 min.
        SessionQnaNotification state = notificationRepository
                .findByIdWindowIdAndIdRecipientUsername(window.getId(), SPEAKER).orElseThrow();
        state.setLastNotifiedAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        notificationRepository.saveAndFlush(state);

        // Two NEW questions arrive after the water mark.
        savePost(window, ATTENDEE, "Q2", null, Instant.now());
        savePost(window, ATTENDEE, "Q3", null, Instant.now().plusMillis(1));

        qnaNotificationService.flushPending(); // should send "2" (only the new ones)

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(2)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), body.capture());
        assertThat(body.getAllValues().get(1)).contains("2");
    }

    // ==================== AC6 (daily) ====================

    @Test
    @DisplayName("AC6: a 'daily' recipient is not re-notified 30 min later (24h throttle)")
    void should_throttleDaily_when_within24h() {
        when(userApiClient.getQnaNotificationFrequency(SPEAKER)).thenReturn("daily");
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now().minus(40, ChronoUnit.MINUTES));

        qnaNotificationService.flushPending(); // sends

        // 30 min elapsed (would re-send if 'live', but daily = 24h throttle).
        SessionQnaNotification state = notificationRepository
                .findByIdWindowIdAndIdRecipientUsername(window.getId(), SPEAKER).orElseThrow();
        state.setLastNotifiedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        notificationRepository.saveAndFlush(state);
        savePost(window, ATTENDEE, "Q2", null, Instant.now());

        qnaNotificationService.flushPending();

        verify(emailService, times(1)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), anyString());
    }

    // ==================== AC7 (off) ====================

    @Test
    @DisplayName("AC7: an 'off' recipient is never notified")
    void should_notNotify_when_preferenceOff() {
        when(userApiClient.getQnaNotificationFrequency(SPEAKER)).thenReturn("off");
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now());

        qnaNotificationService.flushPending();

        verify(emailService, never()).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), anyString());
        assertThat(notificationRepository.count()).isZero();
    }

    // ==================== AC8 (freeze wins) ====================

    @Test
    @DisplayName("AC8: a FROZEN window never generates a digest, even with pending questions")
    void should_notNotify_when_windowFrozen() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.FROZEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now());

        qnaNotificationService.flushPending();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString());
    }

    // ==================== AC9 (exclusions) ====================

    @Test
    @DisplayName("AC9: a reply (non-top-level) does not notify")
    void should_notNotify_when_onlyReplies() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        SessionQnaPost question = savePost(window, ATTENDEE, "Question", null,
                Instant.now().minus(1, ChronoUnit.HOURS));
        // The speaker already answered it once (this also created the question above). A fresh
        // reply must not trigger a notification.
        savePost(window, "another.attendee", "A reply", question.getId(), Instant.now());

        // Notify once for the question, then mark notified through it.
        qnaNotificationService.flushPending();
        SessionQnaNotification state = notificationRepository
                .findByIdWindowIdAndIdRecipientUsername(window.getId(), SPEAKER).orElseThrow();
        state.setLastNotifiedAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        notificationRepository.saveAndFlush(state);

        // Add only a reply (to the existing question) — no new top-level question.
        savePost(window, "third.attendee", "Another reply", question.getId(), Instant.now());

        qnaNotificationService.flushPending();

        // Exactly one email overall (for the original question) — the replies didn't add any.
        verify(emailService, times(1)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), anyString());
    }

    @Test
    @DisplayName("AC9: a self-post does not notify its own author, but does notify the other recipient")
    void should_notNotifyAuthor_butNotifyOther_when_selfPost() {
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        saveSpeaker(session, MODERATOR, SessionUser.SpeakerRole.MODERATOR);
        // The SPEAKER themselves posts the only question.
        savePost(window, SPEAKER, "I'll seed a question", null, Instant.now());

        qnaNotificationService.flushPending();

        verify(emailService, never()).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), anyString());
        verify(emailService, times(1)).sendHtmlEmailSync(eq("the.moderator@batbern.ch"),
                anyString(), anyString());
    }

    // ==================== AC10 (enrichment: missing email skipped; non-DE/EN → EN) ====================

    @Test
    @DisplayName("AC10: a recipient with no resolvable email is skipped")
    void should_skip_when_noEmail() {
        when(userApiClient.getEmailByUsername(SPEAKER)).thenReturn(null);
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now());

        qnaNotificationService.flushPending();

        verify(emailService, never()).sendHtmlEmailSync(anyString(), anyString(), anyString());
        assertThat(notificationRepository.count()).isZero();
    }

    @Test
    @DisplayName("AC10: a non-DE/EN locale preference renders the English template")
    void should_renderEnglish_when_nonDeEnLocale() {
        when(userApiClient.getPreferredLanguage(SPEAKER)).thenReturn("fr");
        SessionQnaWindow window = openWindow(QnaWindowStatus.OPEN);
        Session session = sessionRepository.findById(window.getSessionId()).orElseThrow();
        saveSpeaker(session, SPEAKER, SessionUser.SpeakerRole.PRIMARY_SPEAKER);
        savePost(window, ATTENDEE, "Q1", null, Instant.now());

        qnaNotificationService.flushPending();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmailSync(eq("primary.speaker@batbern.ch"),
                anyString(), body.capture());
        assertThat(body.getValue()).contains("question"); // EN copy
        assertThat(body.getValue()).doesNotContain("Frage"); // not the DE template
    }

    // ==================== helpers ====================

    private SessionQnaWindow openWindow(QnaWindowStatus status) {
        Event event = saveEvent();
        Session session = saveSession(event);
        return windowRepository.save(SessionQnaWindow.builder()
                .sessionId(session.getId())
                .eventCode(EVENT_CODE)
                .status(status)
                .opensAt(Instant.now().minus(1, ChronoUnit.DAYS))
                .closesAt(Instant.now().plus(10, ChronoUnit.DAYS))
                .build());
    }

    private Event saveEvent() {
        Event event = new Event();
        event.setEventCode(EVENT_CODE);
        event.setEventNumber(970);
        event.setTitle("Q&A Notify Test Event");
        event.setDate(Instant.now().minus(2, ChronoUnit.DAYS));
        event.setRegistrationDeadline(Instant.now().minus(9, ChronoUnit.DAYS));
        event.setVenueName("Test Venue");
        event.setVenueAddress("Test Address");
        event.setVenueCapacity(150);
        event.setOrganizerUsername("org.user");
        event.setEventType(ch.batbern.events.dto.generated.EventType.EVENING);
        event.setWorkflowState(EventWorkflowState.EVENT_COMPLETED);
        event.setCreatedAt(Instant.now());
        event.setUpdatedAt(Instant.now());
        event.setCreatedBy("org.user");
        event.setUpdatedBy("org.user");
        return eventRepository.save(event);
    }

    private Session saveSession(Event event) {
        return sessionRepository.save(Session.builder()
                .eventId(event.getId())
                .eventCode(event.getEventCode())
                .sessionSlug(SLUG)
                .title("Cloud Native Foundations")
                .sessionType("presentation")
                .startTime(Instant.now().minus(2, ChronoUnit.DAYS))
                .endTime(Instant.now().minus(2, ChronoUnit.DAYS).plus(1, ChronoUnit.HOURS))
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());
    }

    private SessionUser saveSpeaker(Session session, String username, SessionUser.SpeakerRole role) {
        return sessionUserRepository.save(SessionUser.builder()
                .session(session)
                .username(username)
                .speakerRole(role)
                .isConfirmed(true)
                .build());
    }

    private SessionQnaPost savePost(SessionQnaWindow window, String username, String body,
                                    UUID parentPostId, Instant createdAt) {
        return postRepository.save(SessionQnaPost.builder()
                .windowId(window.getId())
                .parentPostId(parentPostId)
                .postedByUsername(username)
                .body(body)
                .createdAt(createdAt)
                .build());
    }
}
