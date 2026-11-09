package ch.batbern.events.contract;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.RegistrationCodes;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.contract.OpenApiContract;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The {@code pattern}s of the EMS specs accept the values EMS really produces and stores (17.0
 * deferred work). The MSW harness found in 17.0c1 that {@code registrationCode}
 * ({@code ^BAT-[0-9]{4}-[0-9]{6}$}, matching none of the 8190 codes in the anonymized database)
 * rejected every real code, because the spec described a format no code ever had.
 *
 * <p>Each value goes through the real controller and the real response is checked against the spec,
 * the path parameter included: produced by {@link RegistrationCodes}, and the older shapes still
 * stored (V4 backfill {@code {eventCode}-reg-{8 hex}}, a 2026-03 data load {@code BAT{n}-EXIST-{n}}
 * and {@code BAT{n}-NEW-{n}}), and an organizer username of the historical events, which carries an
 * umlaut no CUMS username has.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS spec patterns accept the values EMS produces and stores")
class SpecPatternsAcceptProducedValuesIntegrationTest extends AbstractIntegrationTest {

    private static final String REGISTRATIONS = "docs/api/event-registrations-api.openapi.yml";
    private static final String EVENTS = "docs/api/events-core-api.openapi.yml";
    private static final String CODE = "BATbern9018";
    private static final Instant DATE = Instant.parse("2026-06-11T16:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;

    private Event event;

    @BeforeEach
    void seed() {
        cleanUp();
        event = eventRepository.save(Event.builder()
                .eventCode(CODE).title("BATbern 9018 - Patterns").eventNumber(9018)
                .date(DATE).registrationDeadline(DATE.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern").venueAddress("Kornhausstrasse 3, 3013 Bern").venueCapacity(200)
                // A historical event's organizer: no CUMS username has an umlaut (DB check there)
                .organizerUsername("anna.müller").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
    }

    @AfterEach
    void cleanUp() {
        eventRepository.findByEventCode(CODE).ifPresent(e -> {
            registrationRepository.deleteAll(registrationRepository.findByEventId(e.getId()));
            eventRepository.delete(e);
        });
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    @DisplayName("a code RegistrationCodes produces is valid as path parameter and in the response")
    void producedRegistrationCode_matchesSpec() throws Exception {
        String produced = RegistrationCodes.newUnique(CODE, registrationRepository::existsByRegistrationCode);

        assertRegistrationMatchesSpec(produced);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"BATbern9018-reg-1a2b3c4d", "BAT9018-EXIST-123", "BAT9018-NEW-45"})
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    @DisplayName("an older stored code shape is valid as path parameter and in the response")
    void storedLegacyRegistrationCode_matchesSpec(String stored) throws Exception {
        assertRegistrationMatchesSpec(stored);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    @DisplayName("an event of a historical organizer (umlaut username) matches the Event schema")
    void historicalOrganizerUsername_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/" + CODE))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).contains("anna.m");
        assertResponseMatchesSpec(EVENTS, result);
    }

    private void assertRegistrationMatchesSpec(String registrationCode) throws Exception {
        registrationRepository.save(Registration.builder()
                .registrationCode(registrationCode).eventId(event.getId())
                .attendeeUsername("pattern.attendee").attendeeCompanyId("TestCompany")
                .status("confirmed").registrationDate(DATE.minus(30, ChronoUnit.DAYS))
                .build());
        String path = "/api/v1/events/" + CODE + "/registrations/" + registrationCode;
        String body = "{\"status\": \"confirmed\"}";

        // The code is a path parameter of the organizer's PATCH (the public GET by code was
        // removed 2026-11-09); the PATCH answers with the registration.
        List<String> pathErrors = OpenApiContract.load(REGISTRATIONS).validateRequest("PATCH", path, Map.of(),
                Map.of("Authorization", List.of("Bearer contract"), "Content-Type", List.of("application/json")),
                body);
        assertThat(pathErrors).as("PATCH %s against %s", path, REGISTRATIONS).isEmpty();

        MvcResult result = mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk()).andReturn();
        assertThat(result.getResponse().getContentAsString()).contains(registrationCode);
        assertResponseMatchesSpec(REGISTRATIONS, result);
    }
}
