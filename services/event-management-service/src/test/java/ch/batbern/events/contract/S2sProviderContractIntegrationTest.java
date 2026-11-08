package ch.batbern.events.contract;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.domain.Session;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.repository.SessionRepository;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static ch.batbern.shared.test.contract.ProviderContract.assertExamplesMatchSchema;
import static ch.batbern.shared.test.contract.ProviderContract.assertResponseMatchesSpec;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Provider half of the S2S contract (Story 17.0b1): the two EMS operations PCS calls on the public
 * API ({@code EventManagementClientImpl}) run through the real controller and must match the spec
 * PCS's contract test answers from; the spec examples must match the same schema.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("EMS honours the S2S contract PCS is tested against")
class S2sProviderContractIntegrationTest extends AbstractIntegrationTest {

    private static final String ANALYTICS = "docs/api/event-analytics-api.openapi.yml";
    private static final String EVENTS = "docs/api/events-core-api.openapi.yml";
    private static final String CODE = "BATbern9017";
    private static final Instant DATE = Instant.parse("2026-06-11T16:00:00Z");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private SessionRepository sessionRepository;
    @Autowired
    private RegistrationRepository registrationRepository;

    @BeforeEach
    void seed() {
        cleanUp();
        Event event = eventRepository.save(Event.builder()
                .eventCode(CODE).title("BATbern 9017 - Contracts").eventNumber(9017)
                .date(DATE).registrationDeadline(DATE.minus(7, ChronoUnit.DAYS))
                .venueName("Kursaal Bern").venueAddress("Kornhausstrasse 3, 3013 Bern").venueCapacity(200)
                .organizerUsername("organizer.alice").currentAttendeeCount(0)
                .eventType(EventType.EVENING).workflowState(EventWorkflowState.AGENDA_PUBLISHED)
                .build());
        sessionRepository.save(Session.builder()
                .eventId(event.getId()).eventCode(CODE).sessionSlug("contract-keynote")
                .title("Contract keynote").sessionType("presentation")
                .startTime(DATE).endTime(DATE.plus(45, ChronoUnit.MINUTES))
                .build());
        registrationRepository.save(Registration.builder()
                .registrationCode(CODE + "-reg-contract").eventId(event.getId())
                .attendeeUsername("contract.attendee").attendeeCompanyId("ContractCo")
                .status("confirmed").registrationDate(DATE.minus(30, ChronoUnit.DAYS))
                .build());
    }

    @AfterEach
    void cleanUp() {
        eventRepository.findByEventCode(CODE).ifPresent(e -> {
            registrationRepository.deleteAll(registrationRepository.findByEventId(e.getId()));
            sessionRepository.deleteAll(sessionRepository.findByEventId(e.getId()));
            eventRepository.delete(e);
        });
    }

    @Test
    void should_haveValidExamples_forConsumedOperations() {
        assertExamplesMatchSchema(ANALYTICS, "GET", "/api/v1/events/attendance-summary");
        assertExamplesMatchSchema(EVENTS, "GET", "/api/v1/events/{eventCode}");
    }

    @Test
    @WithMockUser(username = "partner.contract", roles = "PARTNER")
    void attendanceSummary_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/attendance-summary")
                        .param("companyName", "ContractCo").param("fromYear", "2026"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.eventCode == '" + CODE + "')].companyAttendees").value(1))
                .andReturn();

        assertResponseMatchesSpec(ANALYTICS, result);
    }

    @Test
    @WithMockUser(username = "organizer.alice", roles = "ORGANIZER")
    void eventWithSessions_matchesSpec() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/events/" + CODE).param("include", "sessions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessions[0].startTime").exists())
                .andReturn();

        assertResponseMatchesSpec(EVENTS, result);
    }
}
