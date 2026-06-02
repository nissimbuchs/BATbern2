package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.config.TestUserApiClientConfig;
import ch.batbern.events.domain.Event;
import ch.batbern.events.dto.generated.EventType;
import ch.batbern.shared.types.EventWorkflowState;
import ch.batbern.events.dto.generated.users.UserResponse;
import ch.batbern.events.entity.AppSettingEntity;
import ch.batbern.events.repository.AppSettingRepository;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.events.client.UserApiClient;
import ch.batbern.shared.service.EmailService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class, TestUserApiClientConfig.class})
class VenueCoordinationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String EVENT_CODE = "BATtest-venue-coord";
    private static final String CONFIG_KEY = "venue.coordination.config";
    private static final String CONFIG_JSON = """
            {
              "venue": { "salutation": "Frau", "name": "Gabriela Senn", "email": "venue@test.invalid" },
              "catering": { "salutation": "Herr", "name": "Stefan Oppliger", "email": "catering@test.invalid" },
              "coordinatorUsername": "nissim.buchs"
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

    @Autowired
    private UserApiClient userApiClient;

    @Autowired
    private EmailService emailService;

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
        event.setOrganizerUsername("nissim.buchs");
        event.setEventType(EventType.FULL_DAY);
        event.setWorkflowState(EventWorkflowState.SLOT_ASSIGNMENT);
        eventRepository.save(event);

        // Coordinator user — override mock returns to provide a real email
        UserResponse coordinator = new UserResponse()
                .id("nissim.buchs")
                .email("nissim.buchs@elca.ch")
                .firstName("Nissim")
                .lastName("Buchs")
                .active(true);
        when(userApiClient.getUserByUsername("nissim.buchs")).thenReturn(coordinator);

        // TestAwsConfig provides a @Primary Mockito mock for EmailService; pure-logic
        // methods like replaceVariables therefore return null by default and crash the
        // preview flow. Re-implement the real substitution algorithm here so the rendered
        // output is real HTML that we can assert on.
        when(emailService.replaceVariables(anyString(), any())).thenAnswer(invocation -> {
            String template = invocation.getArgument(0);
            @SuppressWarnings("unchecked")
            java.util.Map<String, String> vars = invocation.getArgument(1);
            return realReplace(template, vars);
        });

        // sendHtmlEmailSync is void on a Mockito mock → doNothing by default. No stub needed.
    }

    private static String realReplace(String template, java.util.Map<String, String> vars) {
        if (template == null) {
            return null;
        }
        if (vars == null) {
            return template;
        }
        String result = template;
        // Conditional blocks first
        for (var e : vars.entrySet()) {
            String value = e.getValue();
            boolean isEmpty = value == null || value.trim().isEmpty();
            String open = "\\{\\{#" + java.util.regex.Pattern.quote(e.getKey()) + "\\}\\}";
            String close = "\\{\\{/" + java.util.regex.Pattern.quote(e.getKey()) + "\\}\\}";
            var pattern = java.util.regex.Pattern.compile(open + "(.*?)" + close,
                    java.util.regex.Pattern.DOTALL);
            var matcher = pattern.matcher(result);
            result = isEmpty ? matcher.replaceAll("") : matcher.replaceAll("$1");
        }
        // Then simple placeholders
        for (var e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
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
                .andExpect(jsonPath("$.toEmail", is("venue@test.invalid")))
                .andExpect(jsonPath("$.ccEmails").isArray())
                .andExpect(jsonPath("$.ccEmails", org.hamcrest.Matchers.empty()))
                .andExpect(jsonPath("$.replyToEmail", is("nissim.buchs@elca.ch")))
                .andExpect(jsonPath("$.subject", containsString("15.08.2026")))
                .andExpect(content().string(containsString("Frau Senn")))
                // Notes preserve their newlines as <br>
                .andExpect(content().string(containsString("Drei Headsets reichen wieder.<br>")))
                .andExpect(content().string(containsString("Nissim Buchs")));
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
                .andExpect(jsonPath("$.toEmail", is("venue@test.invalid")))
                .andExpect(jsonPath("$.ccEmails[0]", is("catering@test.invalid")))
                // German "und" connector when two recipients are selected
                .andExpect(content().string(containsString("Frau Senn und Herr Oppliger")));
    }

    @Test
    @DisplayName("send_whenBothRecipients_invokesEmailServiceOnceWithToAndCc")
    @WithMockUser(username = "organizer", roles = "ORGANIZER")
    void send_whenBothRecipients_invokesEmailServiceOnceWithToAndCc() throws Exception {
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

        ArgumentCaptor<String> toCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.List<String>> ccCaptor = ArgumentCaptor.forClass(java.util.List.class);
        ArgumentCaptor<String> replyToCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService, times(1)).sendHtmlEmailSync(
                toCaptor.capture(),
                ccCaptor.capture(),
                anyString(),
                anyString(),
                any(),
                replyToCaptor.capture()
        );
        org.assertj.core.api.Assertions.assertThat(toCaptor.getValue()).isEqualTo("venue@test.invalid");
        org.assertj.core.api.Assertions.assertThat(ccCaptor.getValue()).containsExactly("catering@test.invalid");
        org.assertj.core.api.Assertions.assertThat(replyToCaptor.getValue()).isEqualTo("nissim.buchs@elca.ch");
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
