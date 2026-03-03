package ch.batbern.events.controller;

import ch.batbern.events.config.TestAwsConfig;
import ch.batbern.events.config.TestSecurityConfig;
import ch.batbern.events.dto.generated.TeaserImageItem;
import ch.batbern.events.dto.generated.TeaserImageUploadUrlResponse;
import ch.batbern.events.exception.TeaserImageNotFoundException;
import ch.batbern.events.service.EventTeaserImageService;
import ch.batbern.shared.test.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.net.URI;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for EventTeaserImageController.
 * Story 10.22: Event Teaser Images — AC8 (TDD)
 */
@Import({TestSecurityConfig.class, TestAwsConfig.class})
class EventTeaserImageControllerIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    MockMvc mockMvc;

    @MockBean
    EventTeaserImageService teaserImageService;

    private static final String EVENT_CODE = "BATbern57";

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void generateUploadUrl_asOrganizer_returns200WithUploadUrlAndS3Key() throws Exception {
        TeaserImageUploadUrlResponse response = new TeaserImageUploadUrlResponse(
                URI.create("https://s3.amazonaws.com/test-bucket/key?sig=x"),
                "events/" + EVENT_CODE + "/teaser/abc123.jpg",
                900
        );

        when(teaserImageService.generateUploadUrl(eq(EVENT_CODE), anyString(), anyString()))
                .thenReturn(response);

        mockMvc.perform(post("/api/v1/events/{eventCode}/teaser-images/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileName\":\"teaser.jpg\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.uploadUrl").isNotEmpty())
                .andExpect(jsonPath("$.s3Key").isNotEmpty())
                .andExpect(jsonPath("$.expiresIn").value(900));
    }

    @Test
    void generateUploadUrl_asAnonymous_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/events/{eventCode}/teaser-images/upload-url", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"contentType\":\"image/jpeg\",\"fileName\":\"teaser.jpg\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void confirmUpload_asOrganizer_returns200WithTeaserImageItem() throws Exception {
        UUID imageId = UUID.randomUUID();
        String s3Key = "events/" + EVENT_CODE + "/teaser/" + imageId + ".jpg";

        TeaserImageItem item = new TeaserImageItem(
                imageId,
                URI.create("https://cdn.batbern.ch/" + s3Key),
                0
        );

        when(teaserImageService.confirmUpload(eq(EVENT_CODE), anyString())).thenReturn(item);

        mockMvc.perform(post("/api/v1/events/{eventCode}/teaser-images/confirm", EVENT_CODE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"s3Key\":\"" + s3Key + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.imageUrl").isNotEmpty())
                .andExpect(jsonPath("$.displayOrder").value(0));
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_asOrganizer_returns204() throws Exception {
        UUID imageId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/events/{eventCode}/teaser-images/{imageId}", EVENT_CODE, imageId))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(roles = "ORGANIZER")
    void deleteTeaserImage_whenNotFound_returns404() throws Exception {
        UUID imageId = UUID.randomUUID();
        doThrow(new TeaserImageNotFoundException(imageId.toString()))
                .when(teaserImageService).deleteTeaserImage(EVENT_CODE, imageId);

        mockMvc.perform(delete("/api/v1/events/{eventCode}/teaser-images/{imageId}", EVENT_CODE, imageId))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteTeaserImage_asAnonymous_returns403() throws Exception {
        UUID imageId = UUID.randomUUID();

        mockMvc.perform(delete("/api/v1/events/{eventCode}/teaser-images/{imageId}", EVENT_CODE, imageId))
                .andExpect(status().isForbidden());
    }
}
