package ch.batbern.events.integration;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.domain.SessionUser;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.dto.generated.users.InvitationCredentialsResponse;
import ch.batbern.events.dto.generated.users.ProvisionUserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.events.repository.SessionUserRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.service.OrganizerNotificationService;
import ch.batbern.events.service.SpeakerAcceptanceEmailService;
import ch.batbern.events.service.SpeakerAutoRegistrationService;
import ch.batbern.events.service.SpeakerInvitationEmailService;
import ch.batbern.events.service.SpeakerWorkflowService;
import ch.batbern.events.service.workflow.TransitionPayload;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * End-to-end integration: drive the speaker workflow at the three trigger points enumerated
 * in the spec and assert that {@code registrations} rows appear with the correct
 * {@code metadata.autoRegisteredFrom} value.
 *
 * <p>Spec: {@code _bmad-output/implementation-artifacts/spec-auto-participant-email-aliases-excel-export.md}
 * (F1). Runs against Testcontainers PostgreSQL via {@link AbstractIntegrationTest}.
 */
@Transactional
@Import({TestSecurityConfig.class, TestUserApiClientConfig.class})
class SpeakerAutoRegistrationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private SpeakerWorkflowService workflowService;
    @Autowired
    private SpeakerAutoRegistrationService autoReg;
    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private SessionUserRepository sessionUserRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private UserApiClient userApiClient;

    @MockitoBean
    private SpeakerInvitationEmailService invitationEmailService;
    @MockitoBean
    private SpeakerAcceptanceEmailService acceptanceEmailService;
    @MockitoBean
    private OrganizerNotificationService organizerNotificationService;

    private Event event;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.clearInvocations(userApiClient);
        registrationRepository.deleteAll();
        sessionUserRepository.deleteAll();
        sessionRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        eventRepository.deleteAll();

        event = Event.builder()
                .eventCode("BATbernSAR")
                .eventNumber(998)
                .title("Speaker Auto-Reg Test")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(100)
                .organizerUsername("test-organizer")
                .build();
        event = eventRepository.save(event);

        when(userApiClient.provisionUserWithRole(any()))
                .thenAnswer(inv -> new ProvisionUserResponse("speaker.user", true));
        when(userApiClient.issueInvitationCredentials(any())).thenAnswer(inv ->
                new InvitationCredentialsResponse(
                        InvitationCredentialsResponse.ActionEnum.FRESH_TEMP_PASSWORD)
                        .temporaryPassword("Test1234!@#abcde"));
    }

    @Test
    @DisplayName("CONTACTED → READY auto-registers the speaker (TRIGGER=SESSION_PRIMARY_SPEAKER)")
    void should_autoRegister_when_speakerPromotedToReady() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.CONTACTED, false);

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.READY, "test-organizer",
                TransitionPayload.builder()
                        .email("speaker.user@example.com")
                        .firstName("Speaker")
                        .lastName("User")
                        .build());

        SpeakerPool persisted = speakerPoolRepository.findById(speaker.getId()).orElseThrow();
        assertThat(persisted.getStatus()).isEqualTo(SpeakerWorkflowState.READY);

        Optional<Registration> reg = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), "speaker.user");
        assertThat(reg).isPresent();
        assertThat(reg.get().getStatus()).isEqualTo("confirmed");
        assertThat(reg.get().getMetadata())
                .containsEntry("autoRegisteredFrom", "SESSION_PRIMARY_SPEAKER");
    }

    @Test
    @DisplayName("INVITED → ACCEPTED auto-registers (TRIGGER=POOL_ACCEPTED)")
    void should_autoRegister_when_invitedToAccepted() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.INVITED, true);

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.ACCEPTED,
                "speaker.user", TransitionPayload.builder().build());

        Optional<Registration> reg = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), "speaker.user");
        assertThat(reg).isPresent();
        assertThat(reg.get().getMetadata())
                .containsEntry("autoRegisteredFrom", "POOL_ACCEPTED");
    }

    @Test
    @DisplayName("READY → ACCEPTED (on-behalf) auto-registers (TRIGGER=POOL_ACCEPTED_ON_BEHALF)")
    void should_autoRegister_when_readyToAcceptedOnBehalf() {
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.READY, true);

        workflowService.transition(speaker.getId(), SpeakerWorkflowState.ACCEPTED,
                "test-organizer",
                TransitionPayload.builder().reason("Confirmed via email 2026-05-28").build());

        Optional<Registration> reg = registrationRepository
                .findByEventIdAndAttendeeUsername(event.getId(), "speaker.user");
        assertThat(reg).isPresent();
        assertThat(reg.get().getMetadata())
                .containsEntry("autoRegisteredFrom", "POOL_ACCEPTED_ON_BEHALF");
    }

    @Test
    @DisplayName("Idempotent: re-running ACCEPTED transition does not create a second row")
    void should_beIdempotent_when_acceptedTriggerFiresTwice() {
        // First trigger via the workflow.
        SpeakerPool speaker = createSpeaker(SpeakerWorkflowState.INVITED, true);
        workflowService.transition(speaker.getId(), SpeakerWorkflowState.ACCEPTED,
                "speaker.user", TransitionPayload.builder().build());

        long firstCount = registrationRepository.findByEventId(event.getId()).size();
        assertThat(firstCount).isEqualTo(1L);

        // Re-running the same trigger source directly must be a no-op.
        autoReg.autoRegisterIfAbsent(event.getId(), "speaker.user",
                SpeakerAutoRegistrationService.TRIGGER_POOL_ACCEPTED);

        assertThat(registrationRepository.findByEventId(event.getId())).hasSize(1);
    }

    @Test
    @DisplayName("Past event: no registration created")
    void should_skip_when_eventInPast() {
        Event pastEvent = Event.builder()
                .eventCode("BATbernPAST")
                .eventNumber(7)
                .title("Past Event")
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .date(Instant.now().minus(30, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().minus(40, ChronoUnit.DAYS))
                .venueName("Test Venue")
                .venueAddress("Bern")
                .venueCapacity(100)
                .organizerUsername("test-organizer")
                .build();
        pastEvent = eventRepository.save(pastEvent);

        autoReg.autoRegisterIfAbsent(pastEvent.getId(), "speaker.user",
                SpeakerAutoRegistrationService.TRIGGER_POOL_ACCEPTED);

        List<Registration> regs = registrationRepository.findByEventId(pastEvent.getId());
        assertThat(regs).isEmpty();
    }

    /**
     * Seed a speaker. When {@code provisionSession=true}, also provision a Session +
     * PRIMARY_SPEAKER session_users row so that PrimarySpeakerResolver and the
     * runAcceptedHook auto-reg call find a canonical username.
     */
    private SpeakerPool createSpeaker(SpeakerWorkflowState state, boolean provisionSession) {
        SpeakerPool speaker = new SpeakerPool();
        speaker.setEventId(event.getId());
        speaker.setSpeakerName("Speaker " + UUID.randomUUID().toString().substring(0, 8));
        speaker.setCompany("Tech");
        speaker.setExpertise("Architecture");
        speaker.setStatus(state);
        speaker = speakerPoolRepository.save(speaker);

        if (provisionSession) {
            Session session = Session.builder()
                    .eventId(event.getId())
                    .eventCode(event.getEventCode())
                    .title("Seeded session " + UUID.randomUUID())
                    .sessionSlug("sar-" + UUID.randomUUID())
                    .sessionType("presentation")
                    .speakerPoolId(speaker.getId())
                    .build();
            session = sessionRepository.save(session);

            SessionUser su = SessionUser.builder()
                    .session(session)
                    .username("speaker.user")
                    .speakerRole(SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                    .isConfirmed(false)
                    .build();
            sessionUserRepository.save(su);

            speaker.setSessionId(session.getId());
            speaker = speakerPoolRepository.save(speaker);
        }
        return speaker;
    }
}
