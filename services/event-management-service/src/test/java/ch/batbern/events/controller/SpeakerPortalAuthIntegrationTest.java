package ch.batbern.events.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.SpeakerPool;
import ch.batbern.events.dto.SpeakerResponseRequest;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.SpeakerPoolRepository;
import ch.batbern.events.repository.SpeakerStatusHistoryRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.mockito.Mockito;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.shared.types.SpeakerResponseType;
import ch.batbern.shared.types.SpeakerWorkflowState;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Story 11.E.3 AC5 — auth matrix for the Cognito-secured speaker-portal endpoints.
 *
 * <p>Covers:
 * <ul>
 *   <li>401 when no Authorization header is present (Spring Security's default chain).</li>
 *   <li>403 when an ORGANIZER token (no SPEAKER role) hits any portal endpoint
 *       (denied by {@code @PreAuthorize("hasRole('SPEAKER')")}.</li>
 *   <li>403 when a PARTNER token (no SPEAKER role) hits any portal endpoint.</li>
 *   <li>200 when a SPEAKER token operates on their own event (AC2 + AC3 happy path).</li>
 *   <li>403 when a SPEAKER token targets an {@code eventCode} they have no pool row for
 *       (AC3 pool-ownership invariant).</li>
 *   <li>200 when a SPEAKER+ORGANIZER dual-role token hits the dashboard (AC5 #4).</li>
 * </ul>
 *
 * <p>Uses real PostgreSQL via Testcontainers per project-context.md. The tokens are minted
 * via Spring Security's {@code @WithMockUser} — that is the established pattern in this
 * service (see {@code SpeakerPromoteControllerIntegrationTest}); Cognito JWTs themselves
 * are mocked at the resource-server level by the test profile.
 */
@Transactional
class SpeakerPortalAuthIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private SpeakerPoolRepository speakerPoolRepository;

    @Autowired
    private ch.batbern.events.repository.SessionRepository sessionRepository;

    @Autowired
    private ch.batbern.events.repository.SessionUserRepository sessionUserRepository;

    @Autowired
    private SpeakerStatusHistoryRepository statusHistoryRepository;

    /**
     * UserApiClient is mocked because the dashboard happy-path fetches the speaker's
     * profile for profile-completeness; without a mock the call fails the integration test
     * with a 500 (no user service available in Testcontainers).
     */
    @MockitoBean
    private UserApiClient userApiClient;

    private static final String EVENT_CODE = "BATbern777";
    private static final String FOREIGN_EVENT_CODE = "BATbern778";
    private static final String SPEAKER_USERNAME = "alice.speaker";
    private static final String ORGANIZER_USERNAME = "organizer.user";

    private Event testEvent;
    private Event foreignEvent;

    @BeforeEach
    void setUp() {
        statusHistoryRepository.deleteAll();
        sessionUserRepository.deleteAll();
        speakerPoolRepository.deleteAll();
        sessionRepository.deleteAll();
        eventRepository.deleteAll();

        testEvent = eventRepository.save(Event.builder()
                .eventCode(EVENT_CODE)
                .eventNumber(777)
                .title("Auth Matrix Event")
                .date(Instant.now().plus(60, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(50, ChronoUnit.DAYS))
                .venueName("Venue")
                .venueAddress("Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername(ORGANIZER_USERNAME)
                .build());

        foreignEvent = eventRepository.save(Event.builder()
                .eventCode(FOREIGN_EVENT_CODE)
                .eventNumber(778)
                .title("Foreign Event (no pool row for Alice)")
                .date(Instant.now().plus(90, ChronoUnit.DAYS))
                .registrationDeadline(Instant.now().plus(80, ChronoUnit.DAYS))
                .venueName("Venue")
                .venueAddress("Address")
                .venueCapacity(100)
                .eventType(ch.batbern.events.dto.generated.EventType.EVENING)
                .workflowState(EventWorkflowState.SPEAKER_IDENTIFICATION)
                .organizerUsername(ORGANIZER_USERNAME)
                .build());

        // Alice has an INVITED pool row on testEvent only — none on foreignEvent.
        // contentStatus="PENDING" satisfies the dashboard's Map.getOrDefault assertion
        // (the immutable CONTENT_STATUS_LABELS map rejects null keys).
        // 2026-05-21 (BATbern75 follow-up) — the dashboard's canonical source is now
        // session_users, so a Session + PRIMARY_SPEAKER session_users row are needed too
        // (mirrors the Story 11.E.8 provisionSessionAndPrimarySpeaker shape).
        ch.batbern.events.domain.Session aliceSession = sessionRepository.save(
                ch.batbern.events.domain.Session.builder()
                        .eventId(testEvent.getId())
                        .eventCode(EVENT_CODE)
                        .sessionSlug("auth-matrix-alice-session")
                        .title("Alice's Session")
                        .sessionType("presentation")
                        .build());
        SpeakerPool alicePool = speakerPoolRepository.save(SpeakerPool.builder()
                .eventId(testEvent.getId())
                .sessionId(aliceSession.getId())
                .speakerName("Alice Speaker")
                .email("alice@example.com")
                .username(SPEAKER_USERNAME)
                .status(SpeakerWorkflowState.INVITED)
                .build());
        aliceSession.setSpeakerPoolId(alicePool.getId());
        sessionRepository.save(aliceSession);
        sessionUserRepository.save(ch.batbern.events.domain.SessionUser.builder()
                .session(aliceSession)
                .username(SPEAKER_USERNAME)
                .speakerRole(ch.batbern.events.domain.SessionUser.SpeakerRole.PRIMARY_SPEAKER)
                .isConfirmed(false)
                .build());

        // Dashboard happy-path fetches the speaker's user profile for profile-completeness;
        // return null (no profile yet) so the service falls through gracefully without
        // making an HTTP call to the user service.
        Mockito.when(userApiClient.getUserByUsername(Mockito.anyString())).thenReturn(null);
    }

    // ---------- 401 paths ----------

    @Test
    @DisplayName("should_rejectUnauthenticated_when_noAuthHeader_onRespond")
    void shouldRejectUnauthenticated_whenNoAuthHeader_onRespond() throws Exception {
        SpeakerResponseRequest body = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        // Code review 2026-05-18 (P8): pin to the actual MockMvc behaviour (403 via Spring's
        // default access-denied handler when no JWT is present). Production-side AWS API
        // Gateway returns 401 before the request reaches Spring at all, which is a separate
        // concern handled at the gateway layer; this assertion guards the EMS contract.
        // Pinning to one value catches regressions if the auth chain changes.
        mockMvc.perform(post("/api/v1/speaker-portal/events/" + EVENT_CODE + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_rejectUnauthenticated_when_noAuthHeader_onDashboard")
    void shouldRejectUnauthenticated_whenNoAuthHeader_onDashboard() throws Exception {
        // Code review 2026-05-18 (P8): same pinning as above.
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                .andExpect(status().isForbidden());
    }

    // ---------- 403 paths (wrong role) ----------

    @Test
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    @DisplayName("should_return403_when_organizerToken_onDashboard")
    void shouldReturn403_whenOrganizerToken_onDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "partner.user", roles = {"PARTNER"})
    @DisplayName("should_return403_when_partnerToken_onDashboard")
    void shouldReturn403_whenPartnerToken_onDashboard() throws Exception {
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = ORGANIZER_USERNAME, roles = {"ORGANIZER"})
    @DisplayName("should_return403_when_organizerToken_onRespond")
    void shouldReturn403_whenOrganizerToken_onRespond() throws Exception {
        SpeakerResponseRequest body = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        mockMvc.perform(post("/api/v1/speaker-portal/events/" + EVENT_CODE + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ---------- 200 path (speaker own event) ----------

    @Test
    @WithMockUser(username = SPEAKER_USERNAME, roles = {"SPEAKER"})
    @DisplayName("should_succeed_when_speakerToken_onOwnDashboard")
    void shouldSucceed_whenSpeakerToken_onOwnDashboard() throws Exception {
        // Code review 2026-05-18 (P18): assert the response body shape, not just the status.
        // A 200 with malformed JSON would silently pass the old check; the dashboard contract
        // is that upcomingEvents includes the seeded eventCode in this fixture.
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.speakerName").isString())
                .andExpect(jsonPath("$.upcomingEvents").isArray())
                .andExpect(jsonPath("$.upcomingEvents[0].eventCode").value(EVENT_CODE));
    }

    // ---------- 403 path (speaker, foreign event — AC3 pool-ownership invariant) ----------

    @Test
    @WithMockUser(username = SPEAKER_USERNAME, roles = {"SPEAKER"})
    @DisplayName("should_return403_when_speakerToken_onForeignEventEndpoint")
    void shouldReturn403_whenSpeakerToken_onForeignEventEndpoint() throws Exception {
        SpeakerResponseRequest body = SpeakerResponseRequest.builder()
                .response(SpeakerResponseType.ACCEPT)
                .build();

        // Alice has no pool row on foreignEvent — the authorization helper returns 403.
        mockMvc.perform(post("/api/v1/speaker-portal/events/" + FOREIGN_EVENT_CODE + "/respond")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isForbidden());
    }

    // ---------- 200 path (dual-role SPEAKER + ORGANIZER) ----------

    @Test
    @WithMockUser(username = SPEAKER_USERNAME, roles = {"SPEAKER", "ORGANIZER"})
    @DisplayName("should_succeed_when_dualRoleSpeakerOrganizerToken_onDashboard")
    void shouldSucceed_whenDualRoleSpeakerOrganizerToken_onDashboard() throws Exception {
        // AC5 #4: SPEAKER + ORGANIZER token reaches the controller; AC3's pool check
        // governs whether the operation actually succeeds. The dashboard aggregates
        // across the authenticated speaker's pool rows, so it returns 200 unconditionally
        // (empty list if no rows match — but Alice has one).
        mockMvc.perform(get("/api/v1/speaker-portal/dashboard"))
                .andExpect(status().isOk());
    }
}
