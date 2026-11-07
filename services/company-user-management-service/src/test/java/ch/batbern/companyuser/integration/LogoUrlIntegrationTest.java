package ch.batbern.companyuser.integration;

import ch.batbern.companyuser.config.TestAwsConfig;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Logo URL-based endpoints
 * Tests the /upload-from-url endpoint
 */
@Transactional
@Import(TestAwsConfig.class)
class LogoUrlIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("Should return 400 when uploading from null URL")
    void shouldReturn400_whenUploadingFromNullUrl() throws Exception {
        mockMvc.perform(post("/api/v1/logos/upload-from-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("Should return 400 when uploading from empty URL")
    void shouldReturn400_whenUploadingFromEmptyUrl() throws Exception {
        mockMvc.perform(post("/api/v1/logos/upload-from-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Should return 401 when uploading without auth")
    void shouldReturn401_whenUploadingWithoutAuth() throws Exception {
        mockMvc.perform(post("/api/v1/logos/upload-from-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/logo.png\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    @DisplayName("should_return405_when_removedFetchFromUrlIsCalled")
    void should_return405_when_removedFetchFromUrlIsCalled() throws Exception {
        // Story 17.1 removed POST /logos/fetch-from-url. The path still matches the surviving
        // DELETE /logos/{uploadId}, so Spring answers 405: the path is known, the operation is not.
        mockMvc.perform(post("/api/v1/logos/fetch-from-url")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://example.com/logo.png\"}"))
                .andExpect(status().isMethodNotAllowed());
    }
}
