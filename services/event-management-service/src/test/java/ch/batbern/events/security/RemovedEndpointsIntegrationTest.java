package ch.batbern.events.security;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Story 17.1: the orphan operations removed from the EMS specs have no handler any more. An
 * organizer (who could call them before) gets 404, or 405 where a surviving sibling route shares
 * the path (owner decision 2026-11-07): {@code DELETE …/materials/{materialId}} and
 * {@code POST …/publish/{phase}}. Either way a caller that still uses one fails loudly.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("Removed EMS endpoints (Story 17.1)")
class RemovedEndpointsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @CsvSource({
        "POST, /api/v1/events/BATbern999/speakers/invite-batch,                              404",
        "POST, /api/v1/admin/backfill-speaker-names,                                         404",
        "POST, /api/v1/events/BATbern999/slides-online/send,                                 404",
        "POST, /api/v1/events/BATbern999/sessions/some-session/materials/upload-from-url,    405",
        "GET,  /api/v1/events/BATbern999/publish/preview,                                    405",
    })
    @DisplayName("should_returnNoOperation_when_removedOperationIsCalled")
    void should_returnNoOperation_when_removedOperationIsCalled(String method, String path, int expected)
            throws Exception {
        mockMvc.perform(request(HttpMethod.valueOf(method), path)
                        .with(user("anna.organizer").roles("ORGANIZER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().is(expected));
    }
}
