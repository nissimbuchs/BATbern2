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
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.1: the orphan operations removed from the EMS specs have no handler any more. An
 * organizer (who could call them before) gets 404, or 405 where a surviving sibling route shares
 * the path (owner decision 2026-11-07): {@code DELETE …/materials/{materialId}}. Either way a
 * caller that still uses one fails loudly. Story 4.9b added the event lifecycle paths it replaced
 * ({@code …/publish}, {@code …/publish/{phase}}, {@code …/unpublish/{phase}},
 * {@code …/publish/schedule}, {@code …/publish/status}, {@code …/workflow/transition}); since
 * {@code POST …/publish/{phase}} is gone, {@code GET …/publish/preview} answers 404, not 405.
 *
 * <p>The event {@code BATbern999} does not exist, so a 404 alone would also pass if a removed
 * handler were still there and answered "event not found". The 404 cases therefore assert the
 * no-handler body, and the positive control shows that a live operation on the same event answers
 * with a different 404. Read-only: no class-level {@code @Transactional} (Test fidelity rules).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Removed EMS endpoints (Stories 17.1, 4.9b)")
class RemovedEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "POST, /api/v1/events/BATbern999/speakers/invite-batch,                              404",
        "POST, /api/v1/admin/backfill-speaker-names,                                         404",
        "POST, /api/v1/events/BATbern999/slides-online/send,                                 404",
        "POST, /api/v1/events/BATbern999/sessions/some-session/materials/upload-from-url,    405",
        "GET,  /api/v1/events/BATbern999/publish/preview,                                    404",
        // Story 4.9b: one transition idiom, no aliases for the old lifecycle paths
        "POST, /api/v1/events/BATbern999/publish,                                            404",
        "POST, /api/v1/events/BATbern999/publish/agenda,                                     404",
        "POST, /api/v1/events/BATbern999/unpublish/agenda,                                   404",
        "POST, /api/v1/events/BATbern999/publish/schedule,                                   404",
        "GET,  /api/v1/events/BATbern999/publish/schedule,                                   404",
        "GET,  /api/v1/events/BATbern999/publish/status,                                     404",
        "POST, /api/v1/events/BATbern999/workflow/transition,                                404",
    })
    @DisplayName("should_returnNoOperation_when_removedOperationIsCalled")
    void should_returnNoOperation_when_removedOperationIsCalled(String method, String path, int expected)
            throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .with(user("anna.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(expected))
                .andExpect(expected == 404
                        ? jsonPath("$.message").value(startsWith("No endpoint " + method + " "))
                        : status().is(expected));
    }

    @Test
    @DisplayName("should_answerDomainNotFound_when_liveOperationTargetsUnknownEvent")
    void should_answerDomainNotFound_when_liveOperationTargetsUnknownEvent() throws Exception {
        mockMvc.perform(request(HttpMethod.GET, "/api/v1/events/BATbern999")
                        .with(user("anna.organizer").roles("ORGANIZER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(not(startsWith("No endpoint"))));
    }
}
