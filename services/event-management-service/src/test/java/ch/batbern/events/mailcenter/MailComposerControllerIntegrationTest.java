package ch.batbern.events.mailcenter;

import ch.batbern.events.client.UserApiClient;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.NewsletterSubscriber;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.NewsletterSubscriberRepository;
import ch.batbern.shared.service.EmailService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Answers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 16.8a AC1, AC4, AC10 against real PostgreSQL: the composer operations on the one mail
 * resource, the organizer gate and the validation the backend owns. Nothing here sends: every
 * request either previews or is rejected before delivery. Synthetic addresses only.
 */
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail composer API: gate and validation (Story 16.8a)")
class MailComposerControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATbern921";
    private static final String BASE = "/api/v1/events/" + EVENT_CODE + "/mails";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private NewsletterSubscriberRepository subscriberRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private UserApiClient userApiClient;
    /** Real render code behind {@code TestAwsConfig}'s bare mock; nothing here sends. */
    @MockitoBean(answers = Answers.CALLS_REAL_METHODS)
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        eventRepository.saveAndFlush(Event.builder()
                .eventCode(EVENT_CODE).title("BATbern Test Event #921").eventNumber(921)
                .date(now.plus(30, ChronoUnit.DAYS)).registrationDeadline(now.plus(29, ChronoUnit.DAYS))
                .venueName("Test Venue").venueAddress("Bern").venueCapacity(200)
                .organizerUsername("organizer").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        subscriberRepository.saveAndFlush(NewsletterSubscriber.builder()
                .email("success+sub921@simulator.amazonses.com").firstName("Sub").language("de")
                .source("explicit").unsubscribeToken("tok-921-" + UUID.randomUUID()).build());
        when(userApiClient.getOrganizerUsernames()).thenReturn(List.of("organizer1", "organizer2"));
        when(userApiClient.getUserByUsername("organizer1"))
                .thenReturn(new UserResponse().email("success+org1@simulator.amazonses.com"));
        when(userApiClient.getUserByUsername("organizer2"))
                .thenReturn(new UserResponse().email("success+org2@simulator.amazonses.com"));
    }

    // ── gate ──────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "ATTENDEE")
    @DisplayName("AC1: preview, send and retry are ORGANIZER-only")
    void nonOrganizer_forbidden() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\",\"target\":\"ORGANIZERS\"}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(BASE + "/" + EVENT_CODE + "-1/retry"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: an unknown event is 404")
    void unknownEvent_notFound() throws Exception {
        mockMvc.perform(post("/api/v1/events/BATbern99999/mails/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\"}"))
                .andExpect(status().isNotFound());
    }

    // ── preview ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC4: a newsletter preview renders for a real subscriber, redacted, never cached")
    void newsletterPreview_rendersRealRecipient() throws Exception {
        mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\",\"locale\":\"de\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(jsonPath("$.recipientIndex").value(0))
                .andExpect(jsonPath("$.recipientCount").value(1))
                .andExpect(jsonPath("$.recipient.address").value("success+sub921@simulator.amazonses.com"))
                .andExpect(jsonPath("$.personalized").value(false))
                .andExpect(jsonPath("$.templateKey").value("newsletter-event"))
                .andExpect(jsonPath("$.locale").value("de"))
                .andExpect(jsonPath("$.organizerCount").value(2))
                .andExpect(jsonPath("$.html", containsString("BATbern Test Event #921")))
                .andExpect(jsonPath("$.html", not(containsString("tok-921-"))))
                .andExpect(jsonPath("$.unknownPlaceholders").isArray());
    }

    // ── AC10: validation ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: own text is rejected until Story 16.5")
    void ownText_rejected() throws Exception {
        preview("{\"audience\":\"NEWSLETTER\",\"contentSource\":\"OWN_TEXT\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: a template of another audience is rejected (registrant notice as newsletter)")
    void foreignTemplate_rejected() throws Exception {
        preview("{\"audience\":\"NEWSLETTER\",\"templateKey\":\"slides-online\"}")
                .andExpect(status().isBadRequest());
        preview("{\"audience\":\"SPEAKERS\",\"templateKey\":\"speaker-invitation\"}")
                .andExpect(status().isBadRequest());
        preview("{\"audience\":\"REGISTRANTS\",\"templateKey\":\"newsletter-event\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: a recipient index outside the list is rejected")
    void recipientIndexOutOfRange_rejected() throws Exception {
        preview("{\"audience\":\"NEWSLETTER\",\"recipientIndex\":5}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: venue needs at least one role, speakers in automatic mode need the reminder type")
    void missingAudienceFields_rejected() throws Exception {
        preview("{\"audience\":\"VENUE\",\"templateKey\":\"venue-timetable\",\"venueRecipients\":[]}")
                .andExpect(status().isBadRequest());
        preview("{\"audience\":\"SPEAKERS\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: an unknown audience is rejected")
    void unknownAudience_rejected() throws Exception {
        preview("{\"audience\":\"EVERYONE\"}").andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC10: a send without a target is rejected and sends nothing")
    void sendWithoutTarget_rejected() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"audience\":\"NEWSLETTER\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(jdbc.queryForObject(
                "SELECT count(*) FROM mail_message WHERE event_code = ?", Integer.class, EVENT_CODE)).isZero();
    }

    // ── AC8: retry ───────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "organizer1", roles = "ORGANIZER")
    @DisplayName("AC8: retry is 404 for an unknown mail and 400 for a mail that is not a newsletter")
    void retry_onlyForNewsletters() throws Exception {
        mockMvc.perform(post(BASE + "/" + EVENT_CODE + "-404/retry"))
                .andExpect(status().isNotFound());

        jdbc.update("""
                INSERT INTO mail_message (mail_key, send_group_id, event_code, audience, template_key,
                    content_source, subject, body_html, sender_username, is_test)
                VALUES (?, ?, ?, 'VENUE', 'venue-timetable', 'TEMPLATE', 'Timetable', '<p>x</p>', 'organizer1', false)
                """, EVENT_CODE + "-7", "grp-921-venue", EVENT_CODE);
        mockMvc.perform(post(BASE + "/" + EVENT_CODE + "-7/retry"))
                .andExpect(status().isBadRequest());
    }

    private org.springframework.test.web.servlet.ResultActions preview(String body) throws Exception {
        return mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(body));
    }
}
