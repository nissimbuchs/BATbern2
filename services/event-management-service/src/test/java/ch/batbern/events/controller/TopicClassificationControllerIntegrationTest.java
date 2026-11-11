package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Collections;
import java.util.stream.Collectors;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code POST /api/v1/topics/classifications} (Epic 17, entry 8): the blob canvas classifies the
 * partner topics it loads from partner-coordination itself. Read-only, so no transaction.
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
@DisplayName("POST /topics/classifications")
class TopicClassificationControllerIntegrationTest extends AbstractIntegrationTest {

    private static final String URL = "/api/v1/topics/classifications";

    @Autowired
    private MockMvc mockMvc;

    private ResultActions classify(String body) throws Exception {
        return mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    @Test
    @DisplayName("organizer: one classification per title, in request order, duplicates kept")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_classifyEachTitle_inRequestOrder_when_organizer() throws Exception {
        classify("""
                {"topics": ["AI in Operations", "Zero Trust", "Workplace Psychology", "AI in Operations"]}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.classifications", hasSize(4)))
                .andExpect(jsonPath("$.classifications[*].topic").value(contains(
                        "AI in Operations", "Zero Trust", "Workplace Psychology", "AI in Operations")))
                .andExpect(jsonPath("$.classifications[*].cluster").value(contains(
                        "AI_ML", "SECURITY", "BUSINESS_OTHER", "AI_ML")))
                .andExpect(jsonPath("$.classifications[0].similarityScore").value(0.85))
                .andExpect(jsonPath("$.classifications[0].relatedPastEventNumbers").value(
                        contains(40, 44, 49, 56, 58)))
                .andExpect(jsonPath("$.classifications[1].relatedPastEventNumbers").value(
                        contains(16, 27, 38, 48, 57)))
                .andExpect(jsonPath("$.classifications[2].similarityScore").value(0.50))
                .andExpect(jsonPath("$.classifications[2].relatedPastEventNumbers", hasSize(0)));
    }

    @Test
    @DisplayName("empty list: 400")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_noTitle() throws Exception {
        classify("{\"topics\": []}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("missing list: 400")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_topicsMissing() throws Exception {
        classify("{}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("empty or blank title: 400")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_titleEmptyOrBlank() throws Exception {
        classify("{\"topics\": [\"Zero Trust\", \"\"]}").andExpect(status().isBadRequest());
        classify("{\"topics\": [\"Zero Trust\", \"   \"]}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("more than 500 titles: 400")
    @WithMockUser(username = "test.organizer", roles = {"ORGANIZER"})
    void should_return400_when_overMaxItems() throws Exception {
        String titles = Collections.nCopies(501, "\"Zero Trust\"").stream().collect(Collectors.joining(","));
        classify("{\"topics\": [" + titles + "]}").andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("speaker: 403")
    @WithMockUser(username = "test.speaker", roles = {"SPEAKER"})
    void should_return403_when_notOrganizer() throws Exception {
        classify("{\"topics\": [\"Zero Trust\"]}").andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("anonymous: 401")
    void should_return401_when_anonymous() throws Exception {
        classify("{\"topics\": [\"Zero Trust\"]}").andExpect(status().isUnauthorized());
    }
}
