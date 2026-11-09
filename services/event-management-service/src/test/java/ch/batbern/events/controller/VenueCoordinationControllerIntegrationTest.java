package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class VenueCoordinationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATtest-venue-coord";
    private static final String CONFIG_KEY = "venue.coordination.config";
    private static final String CONFIG_JSON = """
            {
              "venue": { "salutation": "Frau", "name": "Gabriela Senn", "email": "success+venue@simulator.amazonses.com" },
              "catering": { "salutation": "Herr", "name": "Stefan Oppliger", "email": "success+catering@simulator.amazonses.com" },
              "coordinatorUsername": "anna.coordinator"
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AppSettingRepository appSettingRepository;

    @Autowired
    private EventRepository eventRepository;

    /** CUMS behind the real UserApiClient (Story 17.0b2). */
    @Autowired
    private CumsContractHarness cums;

    @Autowired
    private SesRecorder sesRecorder;

    @BeforeEach
    void setUp() {
        appSettingRepository.findBySettingKey(CONFIG_KEY).ifPresent(appSettingRepository::delete);
        // Re-insert config singleton.
        AppSettingEntity setting = new AppSettingEntity();
        setting.setSettingKey(CONFIG_KEY);
        setting.setSettingValue(CONFIG_JSON);
        setting.setUpdatedBy("test");
        appSettingRepository.save(setting);

        // Insert a test event so {{eventDate}}, {{venueName}} resolve.
        eventRepository.findByEventCode(EVENT_CODE).ifPresent(eventRepository::delete);
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
        eventRepository.save(event);

        // Coordinator user: CUMS answers with a real email
        cums.user("anna.coordinator", "Anna", "Coordinator", "anna.coordinator@example.com");

        sesRecorder.reset();
    }

    @AfterEach
    void resetCums() {
        cums.reset();
    }

    @Test
    @DisplayName("preview_whenSingleRecipient_rendersSubjectAndBody")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void preview_whenSingleRecipient_rendersSubjectAndBody() throws Exception {
        Map<String, Object> body = Map.of(
                "templateKey", "venue-timetable",
                "recipients", java.util.List.of("VENUE"),
                "locale", "de",
                "notes", "Drei Headsets reichen wieder.\nNeue Zeile zwei."
        );

        mockMvc.perform(post("/api/v1/events/{code}/venue-coordination/preview", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toEmail", is("success+venue@simulator.amazonses.com")))
                .andExpect(jsonPath("$.ccEmails").isArray())
                .andExpect(jsonPath("$.ccEmails", org.hamcrest.Matchers.empty()))
                .andExpect(jsonPath("$.replyToEmail", is("anna.coordinator@example.com")))
                .andExpect(jsonPath("$.subject", containsString("Samstag, 15. August 2026")))
                .andExpect(content().string(containsString("Frau Senn")))
                // Notes preserve their newlines as <br>
                .andExpect(content().string(containsString("Drei Headsets reichen wieder.<br>")))
                .andExpect(content().string(containsString("Anna Coordinator")));
    }

    @Test
    @DisplayName("preview_whenBothRecipients_combinesSalutationAndSetsCc")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void preview_whenBothRecipients_combinesSalutationAndSetsCc() throws Exception {
        Map<String, Object> body = Map.of(
                "templateKey", "venue-timetable",
                "recipients", java.util.List.of("VENUE", "CATERING"),
                "locale", "de"
        );

        mockMvc.perform(post("/api/v1/events/{code}/venue-coordination/preview", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toEmail", is("success+venue@simulator.amazonses.com")))
                .andExpect(jsonPath("$.ccEmails[0]", is("success+catering@simulator.amazonses.com")))
                // German "und" connector when two recipients are selected
                .andExpect(content().string(containsString("Frau Senn und Herr Oppliger")));
    }

    @Test
    @DisplayName("send_whenBothRecipients_sendsOneMailWithToAndCc")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void send_whenBothRecipients_sendsOneMailWithToAndCc() throws Exception {
        Map<String, Object> body = Map.of(
                "templateKey", "catering-offerte-request",
                "locale", "de",
                "notes", "",
                "recipients", java.util.List.of("VENUE", "CATERING")
        );

        mockMvc.perform(post("/api/v1/events/{code}/venue-coordination/send", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo[0]").exists())
                .andExpect(jsonPath("$.sentTo[1]").exists());

        // One mail as SES received it: venue in To:, catering in Cc:, the coordinator in Reply-To:
        SesRecorder.RecordedMail mail = sesRecorder.single();
        org.assertj.core.api.Assertions.assertThat(mail.to()).containsExactly("success+venue@simulator.amazonses.com");
        org.assertj.core.api.Assertions.assertThat(mail.cc()).containsExactly("success+catering@simulator.amazonses.com");
        org.assertj.core.api.Assertions.assertThat(mail.replyTo()).containsExactly("anna.coordinator@example.com");
    }

    @Test
    @DisplayName("preview_rendersRealLogoUrl_notPlaceholder")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void preview_rendersRealLogoUrl_notPlaceholder() throws Exception {
        Map<String, Object> body = Map.of(
                "templateKey", "venue-timetable",
                "recipients", java.util.List.of("VENUE"),
                "locale", "de"
        );

        mockMvc.perform(post("/api/v1/events/{code}/venue-coordination/preview", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                // The batbern-default layout footer logo must resolve to the real asset URL …
                .andExpect(content().string(containsString("BATbern_white_logo.png")))
                // … and the raw {{logoUrl}} placeholder must NOT leak into the sent email.
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("{{logoUrl}}"))));
    }

    @Test
    @DisplayName("preview_whenConfigMissing_returns412")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void preview_whenConfigMissing_returns412() throws Exception {
        appSettingRepository.findBySettingKey(CONFIG_KEY).ifPresent(appSettingRepository::delete);

        Map<String, Object> body = Map.of(
                "templateKey", "venue-timetable",
                "recipients", java.util.List.of("VENUE"),
                "locale", "de"
        );

        mockMvc.perform(post("/api/v1/events/{code}/venue-coordination/preview", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isPreconditionFailed());
    }
}
