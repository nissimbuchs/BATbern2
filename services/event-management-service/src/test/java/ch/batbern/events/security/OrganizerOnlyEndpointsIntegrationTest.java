package ch.batbern.events.security;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Event, session, speaker-pool, notification and admin operations are organizer tools. A signed-in
 * attendee, speaker or partner gets 403 before any lookup (the event below does not exist, so a
 * missing role check would answer 404 from the handler instead).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Organizer-only EMS endpoints")
class OrganizerOnlyEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{1} {2} as {0} -> 403")
    @CsvSource({
        "ATTENDEE, DELETE, /api/v1/events/BATbern999",
        "PARTNER,  DELETE, /api/v1/events/BATbern999",
        "SPEAKER,  DELETE, /api/v1/events/BATbern999/sessions/some-session",
        "ATTENDEE, DELETE, /api/v1/events/BATbern999/speakers/pool/00000000-0000-0000-0000-000000000001",
        "PARTNER,  GET,    /api/v1/events/BATbern999/speakers/pool",
        "ATTENDEE, POST,   /api/v1/events/BATbern999/publish",
        "PARTNER,  GET,    /api/v1/notifications?username=anna.organizer",
        "ATTENDEE, DELETE, /api/v1/notifications/00000000-0000-0000-0000-000000000001",
        "SPEAKER,  POST,   /api/v1/admin/backfill-speaker-names",
    })
    @DisplayName("should_return403_when_nonOrganizerCallsOrganizerTool")
    void should_return403_when_nonOrganizerCallsOrganizerTool(String role, String method, String path)
            throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path).with(user("someone").roles(role)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_return403_when_nonOrganizerListsAnotherUsersRegistrations")
    void should_return403_when_nonOrganizerListsAnotherUsersRegistrations() throws Exception {
        mockMvc.perform(get("/api/v1/events/registrations")
                        .header("X-Attendee-Username", "anna.organizer")
                        .with(user("someone").roles("ATTENDEE")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should_rejectAnonymous_when_listingAUsersRegistrations")
    void should_rejectAnonymous_when_listingAUsersRegistrations() throws Exception {
        // GET /api/v1/events/* is public for event pages; this one must not be. The test chain
        // answers an anonymous caller with 403, production with 401: both are a refusal.
        mockMvc.perform(get("/api/v1/events/registrations").header("X-Attendee-Username", "anna.organizer"))
                .andExpect(status().is(anyOf(is(401), is(403))));
    }

    @Test
    @DisplayName("should_reachHandler_when_organizerDeletesUnknownEvent")
    void should_reachHandler_when_organizerDeletesUnknownEvent() throws Exception {
        // Control: the organizer passes the role check and gets the handler's 404.
        mockMvc.perform(request(HttpMethod.DELETE, "/api/v1/events/BATbern999")
                        .with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound());
    }
}
