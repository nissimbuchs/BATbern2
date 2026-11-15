package ch.batbern.events.controller;

import ch.batbern.events.config.CumsContractHarness;
import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.core.dto.generated.EventType;
import ch.batbern.events.domain.Event;
import ch.batbern.events.domain.Registration;
import ch.batbern.events.repository.EventRepository;
import ch.batbern.events.repository.RegistrationRepository;
import ch.batbern.events.service.EventWriteHooks;
import ch.batbern.shared.test.AbstractIntegrationTest;
import ch.batbern.shared.test.mail.SesRecorder;
import ch.batbern.shared.types.EventWorkflowState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PUT and PATCH save the event and fill the waitlist in one transaction (#1316 "one write, one
 * transaction", EventController story): when a module's {@link EventWriteHooks#capacitySaved} fails,
 * the capacity change and the promotions roll back together and the caller gets an error, instead of
 * a saved capacity with an unfilled waitlist.
 *
 * <p>Commits like production (no test transaction): the write's own transaction is what is tested. A
 * test hook, registered after the registrations module's real one, throws for {@link #FAILING} only;
 * {@link #HEALTHY} is the positive control.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class, EventCapacityRollbackIntegrationTest.FailingHook.class})
@DisplayName("Event capacity write rolls back with its waitlist fill")
class EventCapacityRollbackIntegrationTest extends AbstractIntegrationTest {

    private static final String FAILING = "BATbern9750";
    private static final String HEALTHY = "BATbern9751";
    private static final String SIM = "@simulator.amazonses.com";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private RegistrationRepository registrationRepository;
    @Autowired
    private SesRecorder sesRecorder;
    @Autowired
    private CumsContractHarness cums;

    /** Throws for the failing event, after the real hooks ran (no order: lowest precedence). */
    @TestConfiguration
    static class FailingHook {
        @Bean
        EventWriteHooks failingCapacityHook() {
            return new EventWriteHooks() {
                @Override
                public void capacitySaved(Event event) {
                    if (FAILING.equals(event.getEventCode())) {
                        throw new IllegalStateException("capacity hook failed for " + FAILING);
                    }
                }
            };
        }
    }

    @BeforeEach
    void setUp() {
        eventRepository.deleteAll();
        sesRecorder.reset();
        cums.user("wanda.waiting", "{\"email\":\"success+wanda-waiting" + SIM + "\"}");
        for (String code : new String[] {FAILING, HEALTHY}) {
            Event event = eventRepository.save(event(code));
            registration(event, "rita.registered", "registered", null);
            registration(event, "wanda.waiting", "waitlist", 1);
        }
    }

    @AfterEach
    void tearDown() {
        sesRecorder.awaitAsyncSends();
        eventRepository.deleteAll();
        cums.reset();
    }

    @Test
    @DisplayName("should_rollBackCapacityAndPromotion_when_capacityHookFailsOnPatch")
    void should_rollBackCapacityAndPromotion_when_capacityHookFailsOnPatch() throws Exception {
        // Positive control: the same PATCH on the healthy event saves the capacity and promotes
        mockMvc.perform(patch("/api/v1/events/" + HEALTHY).with(user("olga.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"registrationCapacity\":3}"))
                .andExpect(status().isOk());
        assertThat(capacity(HEALTHY)).isEqualTo(3);
        assertThat(registrationStatus(HEALTHY, "wanda.waiting")).isEqualTo("registered");

        mockMvc.perform(patch("/api/v1/events/" + FAILING).with(user("olga.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"registrationCapacity\":3}"))
                // An error answer (GlobalExceptionHandler maps the IllegalStateException), not a 2xx
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        assertThat(capacity(FAILING)).isEqualTo(1);
        assertThat(registrationStatus(FAILING, "wanda.waiting")).isEqualTo("waitlist");
    }

    @Test
    @DisplayName("should_rollBackCapacityAndPromotion_when_capacityHookFailsOnPut")
    void should_rollBackCapacityAndPromotion_when_capacityHookFailsOnPut() throws Exception {
        mockMvc.perform(put("/api/v1/events/" + HEALTHY).with(user("olga.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(putBody("Healthy")))
                .andExpect(status().isOk());
        assertThat(capacity(HEALTHY)).isEqualTo(3);
        assertThat(registrationStatus(HEALTHY, "wanda.waiting")).isEqualTo("registered");

        mockMvc.perform(put("/api/v1/events/" + FAILING).with(user("olga.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON).content(putBody("Not saved")))
                // An error answer (GlobalExceptionHandler maps the IllegalStateException), not a 2xx
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isGreaterThanOrEqualTo(400));

        Event failing = eventRepository.findByEventCode(FAILING).orElseThrow();
        assertThat(failing.getRegistrationCapacity()).isEqualTo(1);
        assertThat(failing.getTitle()).isEqualTo("Rollback " + FAILING);
        assertThat(registrationStatus(FAILING, "wanda.waiting")).isEqualTo("waitlist");
    }

    private Integer capacity(String eventCode) {
        return eventRepository.findByEventCode(eventCode).orElseThrow().getRegistrationCapacity();
    }

    private String registrationStatus(String eventCode, String username) {
        UUID eventId = eventRepository.findByEventCode(eventCode).orElseThrow().getId();
        return registrationRepository.findByEventIdAndAttendeeUsername(eventId, username).orElseThrow().getStatus();
    }

    private static String putBody(String title) {
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return """
                {"title":"%s","date":"%s","registrationDeadline":"%s","venueName":"Kornhausforum",
                 "venueAddress":"Kornhausplatz 18, 3011 Bern","venueCapacity":200,
                 "organizerUsername":"olga.organizer","currentAttendeeCount":0,"eventType":"EVENING",
                 "registrationCapacity":3}"""
                .formatted(title, date, date.minus(7, ChronoUnit.DAYS));
    }

    private static Event event(String code) {
        Instant date = Instant.now().plus(40, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
        return Event.builder()
                .eventCode(code)
                .title("Rollback " + code)
                .eventNumber(Integer.parseInt(code.substring("BATbern".length())))
                .date(date)
                .registrationDeadline(date.minus(7, ChronoUnit.DAYS))
                .venueName("Kornhausforum")
                .venueAddress("Kornhausplatz 18, 3011 Bern")
                .venueCapacity(200)
                .organizerUsername("olga.organizer")
                .currentAttendeeCount(0)
                .eventType(EventType.EVENING)
                .workflowState(EventWorkflowState.CREATED)
                .registrationCapacity(1)
                .build();
    }

    private void registration(Event event, String username, String status, Integer waitlistPosition) {
        registrationRepository.save(Registration.builder()
                .registrationCode(event.getEventCode() + "-" + username)
                .eventId(event.getId())
                .deregistrationToken(UUID.randomUUID())
                .attendeeUsername(username)
                .attendeeFirstName("Capture")
                .attendeeLastName("Rollback")
                .attendeeEmail("success+" + username.replace('.', '-') + SIM)
                .status(status)
                .waitlistPosition(waitlistPosition)
                .registrationDate(Instant.now().minus(1, ChronoUnit.DAYS))
                .build());
    }
}
