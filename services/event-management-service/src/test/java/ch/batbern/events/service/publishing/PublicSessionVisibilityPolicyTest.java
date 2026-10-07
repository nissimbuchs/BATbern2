package ch.batbern.events.service.publishing;

import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Public visibility of sessions and speakers per publishing phase (bug fix 2026-10-07).
 *
 * <ul>
 *   <li>TOPIC (or unpublished): no sessions, no speakers.</li>
 *   <li>SPEAKERS: only speakers whose workflow state is ACCEPTED or later; their sessions are
 *       returned so the speaker grid can render, but the frontend shows no session list.</li>
 *   <li>AGENDA: only sessions with an assigned slot whose speaker is QUALITY_REVIEWED, plus
 *       structural slots (moderation, breaks, lunch, aperitif, networking).</li>
 *   <li>Past or completed events are not filtered (archive).</li>
 * </ul>
 */
class PublicSessionVisibilityPolicyTest {

    private static final Instant NOW = Instant.parse("2026-10-07T10:00:00Z");
    private static final Instant EVENT_DATE = Instant.parse("2026-11-06T16:00:00Z");

    private final PublicSessionVisibilityPolicy policy = new PublicSessionVisibilityPolicy();

    // ----------------------------------------------------------------------------------------
    // TOPIC phase / unpublished
    // ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"topic", "TOPIC", "none", ""})
    @DisplayName("should_returnNoSessions_when_phaseIsTopicOrUnpublished")
    void should_returnNoSessions_when_phaseIsTopicOrUnpublished(String phase) {
        Event event = upcomingEvent(phase);
        Session talk = talk(true);
        Session lunch = structural("lunch", true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk, lunch),
                List.of(pool(talk, SpeakerWorkflowState.QUALITY_REVIEWED)), NOW);

        assertThat(visible).isEmpty();
    }

    // ----------------------------------------------------------------------------------------
    // SPEAKERS phase
    // ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = SpeakerWorkflowState.class,
            names = {"ACCEPTED", "CONTENT_SUBMITTED", "QUALITY_REVIEWED"})
    @DisplayName("should_showSpeaker_when_speakersPhaseAndSpeakerAcceptedOrLater")
    void should_showSpeaker_when_speakersPhaseAndSpeakerAcceptedOrLater(SpeakerWorkflowState state) {
        Event event = upcomingEvent("speakers");
        Session talk = talk(false);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(pool(talk, state)), NOW);

        assertThat(visible).containsExactly(talk);
    }

    @ParameterizedTest
    @EnumSource(value = SpeakerWorkflowState.class,
            names = {"IDENTIFIED", "CONTACTED", "READY", "INVITED", "DECLINED"})
    @DisplayName("should_hideSpeaker_when_speakersPhaseAndSpeakerNotYetAccepted")
    void should_hideSpeaker_when_speakersPhaseAndSpeakerNotYetAccepted(SpeakerWorkflowState state) {
        Event event = upcomingEvent("speakers");
        Session talk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(pool(talk, state)), NOW);

        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("should_hideStructuralAndUnlinkedSessions_when_speakersPhase")
    void should_hideStructuralAndUnlinkedSessions_when_speakersPhase() {
        Event event = upcomingEvent("speakers");
        Session moderation = structural("moderation", true);
        Session unlinkedTalk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(moderation, unlinkedTalk), List.of(), NOW);

        assertThat(visible).isEmpty();
    }

    // ----------------------------------------------------------------------------------------
    // AGENDA phase
    // ----------------------------------------------------------------------------------------

    @Test
    @DisplayName("should_showSession_when_agendaPhaseAndSlotAssignedAndQualityReviewed")
    void should_showSession_when_agendaPhaseAndSlotAssignedAndQualityReviewed() {
        Event event = upcomingEvent("agenda");
        Session talk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk),
                List.of(pool(talk, SpeakerWorkflowState.QUALITY_REVIEWED)), NOW);

        assertThat(visible).containsExactly(talk);
    }

    @Test
    @DisplayName("should_hideSession_when_agendaPhaseAndNoSlotAssigned")
    void should_hideSession_when_agendaPhaseAndNoSlotAssigned() {
        Event event = upcomingEvent("agenda");
        Session talk = talk(false);

        List<Session> visible = policy.filterForPublic(event, List.of(talk),
                List.of(pool(talk, SpeakerWorkflowState.QUALITY_REVIEWED)), NOW);

        assertThat(visible).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(value = SpeakerWorkflowState.class, names = {"QUALITY_REVIEWED"}, mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("should_hideSession_when_agendaPhaseAndSpeakerNotQualityReviewed")
    void should_hideSession_when_agendaPhaseAndSpeakerNotQualityReviewed(SpeakerWorkflowState state) {
        Event event = upcomingEvent("agenda");
        Session talk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(pool(talk, state)), NOW);

        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("should_showStructuralSlot_when_agendaPhaseAndSlotAssigned")
    void should_showStructuralSlot_when_agendaPhaseAndSlotAssigned() {
        Event event = upcomingEvent("agenda");
        Session lunch = structural("lunch", true);
        Session unslottedBreak = structural("break", false);

        List<Session> visible = policy.filterForPublic(event, List.of(lunch, unslottedBreak), List.of(), NOW);

        assertThat(visible).containsExactly(lunch);
    }

    @Test
    @DisplayName("should_hideNonStructuralSession_when_agendaPhaseAndNoSpeakerPoolLink")
    void should_hideNonStructuralSession_when_agendaPhaseAndNoSpeakerPoolLink() {
        Event event = upcomingEvent("agenda");
        Session talk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(), NOW);

        assertThat(visible).isEmpty();
    }

    @Test
    @DisplayName("should_ignorePoolRowsOfOtherSessions_when_filtering")
    void should_ignorePoolRowsOfOtherSessions_when_filtering() {
        Event event = upcomingEvent("agenda");
        Session reviewed = talk(true);
        Session invited = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(reviewed, invited),
                List.of(pool(reviewed, SpeakerWorkflowState.QUALITY_REVIEWED),
                        pool(invited, SpeakerWorkflowState.INVITED)), NOW);

        assertThat(visible).containsExactly(reviewed);
    }

    // ----------------------------------------------------------------------------------------
    // Archive: past or completed events are shown as they are
    // ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @EnumSource(value = EventWorkflowState.class, names = {"EVENT_COMPLETED", "ARCHIVED"})
    @DisplayName("should_notFilter_when_eventCompletedOrArchived")
    void should_notFilter_when_eventCompletedOrArchived(EventWorkflowState state) {
        Event event = upcomingEvent("agenda");
        event.setWorkflowState(state);
        Session talk = talk(false);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(), NOW);

        assertThat(visible).containsExactly(talk);
    }

    @Test
    @DisplayName("should_notFilter_when_eventDateIsBeforeToday")
    void should_notFilter_when_eventDateIsBeforeToday() {
        Event event = upcomingEvent("agenda");
        event.setDate(NOW.minus(30, ChronoUnit.DAYS));
        Session talk = talk(false);

        List<Session> visible = policy.filterForPublic(event, List.of(talk), List.of(), NOW);

        assertThat(visible).containsExactly(talk);
    }

    @Test
    @DisplayName("should_filter_when_eventIsLiveToday")
    void should_filter_when_eventIsLiveToday() {
        Event event = upcomingEvent("agenda");
        event.setWorkflowState(EventWorkflowState.EVENT_LIVE);
        event.setDate(NOW.minus(2, ChronoUnit.HOURS));
        Session talk = talk(true);

        List<Session> visible = policy.filterForPublic(event, List.of(talk),
                List.of(pool(talk, SpeakerWorkflowState.INVITED)), NOW);

        assertThat(visible).isEmpty();
    }

    // ----------------------------------------------------------------------------------------
    // Public read model decisions (2026-10-07)
    // ----------------------------------------------------------------------------------------

    @ParameterizedTest
    @ValueSource(strings = {"topic", "speakers", "agenda", "SPEAKERS"})
    @DisplayName("should_bePubliclyVisible_when_phasePublished")
    void should_bePubliclyVisible_when_phasePublished(String phase) {
        assertThat(policy.isPubliclyVisible(upcomingEvent(phase))).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"none", ""})
    @DisplayName("should_notBePubliclyVisible_when_unpublished")
    void should_notBePubliclyVisible_when_unpublished(String phase) {
        assertThat(policy.isPubliclyVisible(upcomingEvent(phase))).isFalse();
    }

    @ParameterizedTest
    @EnumSource(value = EventWorkflowState.class, names = {"EVENT_COMPLETED", "ARCHIVED"})
    @DisplayName("should_bePubliclyVisible_when_completedOrArchivedWithoutPhase")
    void should_bePubliclyVisible_when_completedOrArchivedWithoutPhase(EventWorkflowState state) {
        Event event = upcomingEvent(null);
        event.setWorkflowState(state);

        assertThat(policy.isPubliclyVisible(event)).isTrue();
    }

    @ParameterizedTest
    @CsvSource({"topic,false", "speakers,false", "agenda,true"})
    @DisplayName("should_publishSessionDetailsOnlyFromAgenda_when_eventUpcoming")
    void should_publishSessionDetailsOnlyFromAgenda_when_eventUpcoming(String phase, boolean expected) {
        assertThat(policy.sessionDetailsPublic(upcomingEvent(phase), NOW)).isEqualTo(expected);
    }

    @Test
    @DisplayName("should_publishSessionDetails_when_eventInThePast")
    void should_publishSessionDetails_when_eventInThePast() {
        Event event = upcomingEvent("speakers");
        event.setDate(NOW.minus(30, ChronoUnit.DAYS));

        assertThat(policy.sessionDetailsPublic(event, NOW)).isTrue();
    }

    @Test
    @DisplayName("should_treatNetworkingAsSpeakerSession_when_filtering")
    void should_treatNetworkingAsSpeakerSession_when_filtering() {
        // networking is not structural (owner decision 2026-10-07): without a reviewed speaker it
        // stays hidden in the AGENDA phase, unlike lunch
        Event event = upcomingEvent("agenda");
        Session networking = structural("networking", true);

        assertThat(policy.filterForPublic(event, List.of(networking), List.of(), NOW)).isEmpty();
    }

    // ----------------------------------------------------------------------------------------
    // Fixtures
    // ----------------------------------------------------------------------------------------

    private Event upcomingEvent(String phase) {
        return Event.builder()
                .id(UUID.randomUUID())
                .eventCode("BATbern60")
                .date(EVENT_DATE)
                .workflowState(EventWorkflowState.SLOT_ASSIGNMENT)
                .currentPublishedPhase(phase)
                .build();
    }

    private Session talk(boolean slotted) {
        return session("presentation", slotted);
    }

    private Session structural(String type, boolean slotted) {
        return session(type, slotted);
    }

    private Session session(String type, boolean slotted) {
        return Session.builder()
                .id(UUID.randomUUID())
                .sessionType(type)
                .startTime(slotted ? EVENT_DATE : null)
                .endTime(slotted ? EVENT_DATE.plus(30, ChronoUnit.MINUTES) : null)
                .build();
    }

    private SpeakerPool pool(Session session, SpeakerWorkflowState state) {
        return SpeakerPool.builder()
                .id(UUID.randomUUID())
                .sessionId(session.getId())
                .status(state)
                .build();
    }
}
