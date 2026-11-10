package ch.batbern.events.mailcenter;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.venuecoordination.VenueCoordinationConfig;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.service.AdminSettingsService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Venue and catering mail through the Mail Center composer (Story 16.8c moved these cases here from
 * the retired {@code /venue-coordination/preview|send} endpoints): salutation, notes and
 * coordinator signature in the render, Reply-To the coordinator, the layout's real logo, To and Cc
 * as SES received them, and 412 without a contact configuration. CUMS is the
 * {@link CumsContractHarness}, SES the {@link SesRecorder}. Not {@code @Transactional}: the
 * recorder commits in its own transaction, so this class removes its own rows.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Mail composer: venue and catering mail")
class VenueComposerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATtest-venue-coord";
    private static final String BASE = "/api/v1/events/" + EVENT_CODE + "/mails";
    private static final String VENUE = "success+venue@simulator.amazonses.com";
    private static final String CATERING = "success+catering@simulator.amazonses.com";
    private static final String CONFIG_JSON = """
            {
              "venue": { "salutation": "Frau", "name": "Gabriela Senn", "email": "%s" },
              "catering": { "salutation": "Herr", "name": "Stefan Oppliger", "email": "%s" },
              "coordinatorUsername": "anna.coordinator"
            }
            """.formatted(VENUE, CATERING);

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private AdminSettingsService adminSettingsService;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private CumsContractHarness cums;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUp() {
        cleanUp();
        adminSettingsService.setSetting(VenueCoordinationConfig.SETTING_KEY, CONFIG_JSON, "test");

        // A test event so {{eventDate}} and {{venueName}} resolve.
        Instant eventDate = Instant.parse("2026-08-15T18:00:00Z");
        Event event = new Event();
        event.setEventCode(EVENT_CODE);
        event.setEventNumber(999);
        event.setTitle("BATtest Venue Coord");
        event.setDescription("Test event for venue coordination");
        event.setVenueName("ZPK");
        event.setVenueAddress("Helvetiapl. 1, Bern");
        event.setVenueCapacity(150);
        event.setDate(eventDate);
        event.setRegistrationDeadline(eventDate.minusSeconds(14L * 24L * 3600L));
        event.setOrganizerUsername("anna.coordinator");
        event.setEventType(EventType.FULL_DAY);
        event.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        eventRepository.saveAndFlush(event);

        // Coordinator: CUMS answers with a real address
        cums.user("anna.coordinator", "Anna", "Coordinator", "anna.coordinator@example.com");
        sesRecorder.reset();
    }

    @AfterEach
    void cleanUp() {
        cums.reset();
        jdbc.update("DELETE FROM mail_message WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM events WHERE event_code = ?", EVENT_CODE);
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("preview, one role: To the venue, no Cc, Reply-To the coordinator, salutation, notes with line breaks")
    void preview_whenSingleRecipient_rendersSubjectAndBody() throws Exception {
        preview("""
                {"audience":"VENUE","templateKey":"venue-timetable","locale":"de","venueRecipients":["VENUE"],
                 "venueNotes":"Drei Headsets reichen wieder.\\nNeue Zeile zwei."}
                """)
                .andExpect(jsonPath("$.recipient.address", is(VENUE)))
                .andExpect(jsonPath("$.recipient.cc", empty()))
                .andExpect(jsonPath("$.replyTo", is("anna.coordinator@example.com")))
                .andExpect(jsonPath("$.subject", containsString("Samstag, 15. August 2026")))
                .andExpect(jsonPath("$.html", containsString("Frau Senn")))
                // Notes keep their line breaks as <br>
                .andExpect(jsonPath("$.html", containsString("Drei Headsets reichen wieder.<br>")))
                .andExpect(jsonPath("$.html", containsString("Anna Coordinator")));
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("preview, both roles: catering in Cc, one combined salutation")
    void preview_whenBothRecipients_combinesSalutationAndSetsCc() throws Exception {
        preview("""
                {"audience":"VENUE","templateKey":"venue-timetable","locale":"de",
                 "venueRecipients":["VENUE","CATERING"]}
                """)
                .andExpect(jsonPath("$.recipient.address", is(VENUE)))
                .andExpect(jsonPath("$.recipient.cc[0]", is(CATERING)))
                // German "und" between the two salutations
                .andExpect(jsonPath("$.html", containsString("Frau Senn und Herr Oppliger")));
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("send, both roles: one mail, venue in To, catering in Cc, the coordinator in Reply-To")
    void send_whenBothRecipients_sendsOneMailWithToAndCc() throws Exception {
        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"audience":"VENUE","templateKey":"catering-offerte-request","locale":"de","venueNotes":"",
                         "venueRecipients":["VENUE","CATERING"],"target":"RECIPIENTS"}
                        """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.recipientCount", is(1)));

        // One mail as SES received it
        SesRecorder.RecordedMail mail = sesRecorder.single();
        assertThat(mail.to()).containsExactly(VENUE);
        assertThat(mail.cc()).containsExactly(CATERING);
        assertThat(mail.replyTo()).containsExactly("anna.coordinator@example.com");
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("preview: the layout's logo is the real asset, never the raw {{logoUrl}}")
    void preview_rendersRealLogoUrl_notPlaceholder() throws Exception {
        preview("""
                {"audience":"VENUE","templateKey":"venue-timetable","locale":"de","venueRecipients":["VENUE"]}
                """)
                .andExpect(jsonPath("$.html", containsString("BATbern_white_logo.png")))
                .andExpect(jsonPath("$.html", not(containsString("{{logoUrl}}"))));
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("preview without a contact configuration → 412")
    void preview_whenConfigMissing_returns412() throws Exception {
        jdbc.update("DELETE FROM app_settings WHERE setting_key = ?", VenueCoordinationConfig.SETTING_KEY);

        mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content("""
                        {"audience":"VENUE","templateKey":"venue-timetable","locale":"de","venueRecipients":["VENUE"]}
                        """))
                .andExpect(status().isPreconditionFailed());
    }

    @Test
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    @DisplayName("send to a stored contact on a reserved domain → 422 naming the domain, nothing sent")
    void send_whenContactOnReservedDomain_returns422() throws Exception {
        // Written past the setting's validation, as an old row or the anonymized local snapshot holds it
        jdbc.update("UPDATE app_settings SET setting_value = ? WHERE setting_key = ?",
                CONFIG_JSON.replace(VENUE, "redacted@example.invalid"), VenueCoordinationConfig.SETTING_KEY);

        mockMvc.perform(post(BASE).contentType(MediaType.APPLICATION_JSON).content("""
                        {"audience":"VENUE","templateKey":"venue-timetable","locale":"de",
                         "venueRecipients":["VENUE"],"target":"RECIPIENTS"}
                        """))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status", is(422)))
                .andExpect(jsonPath("$.message", containsString("example.invalid")))
                .andExpect(jsonPath("$.message", not(containsString("redacted"))));
        assertThat(sesRecorder.mails()).isEmpty();
    }

    private ResultActions preview(String request) throws Exception {
        return mockMvc.perform(post(BASE + "/preview").contentType(MediaType.APPLICATION_JSON).content(request))
                .andExpect(status().isOk());
    }
}
